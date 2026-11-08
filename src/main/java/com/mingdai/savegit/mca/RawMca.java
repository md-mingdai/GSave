package com.mingdai.savegit.mca;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 字节级 Anvil 区域文件读写器，不依赖任何 NBT 库。
 * 区块记录始终以磁盘上的原始字节保存，往返完全一致。
 */
public final class RawMca {
    public static final int SECTOR_BYTES = 4096;
    public static final int HEADER_BYTES = 8192;
    public static final int LOCATION_COUNT = 1024;
    /** 单个区块记录的最大字节数：255 个扇区。 */
    public static final int MAX_CHUNK_RECORD_BYTES = 255 * SECTOR_BYTES;
    public static final int EXTERNAL_SECTOR_COUNT = 255;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RawMca() {
    }

    public static final class Location {
        public final int offset;
        public final int sectorCount;

        public Location(int offset, int sectorCount) {
            this.offset = offset;
            this.sectorCount = sectorCount;
        }

        public boolean isPresent() {
            return offset > 0 && sectorCount > 0;
        }

        public boolean isExternal() {
            return offset > 0 && sectorCount == EXTERNAL_SECTOR_COUNT;
        }
    }

    public static Location[] readLocations(byte[] header) {
        Location[] locations = new Location[LOCATION_COUNT];
        if (header.length < HEADER_BYTES) {
            for (int i = 0; i < LOCATION_COUNT; i++) {
                locations[i] = new Location(0, 0);
            }
            return locations;
        }

        for (int i = 0; i < LOCATION_COUNT; i++) {
            int b0 = header[i * 4] & 0xFF;
            int b1 = header[i * 4 + 1] & 0xFF;
            int b2 = header[i * 4 + 2] & 0xFF;
            int b3 = header[i * 4 + 3] & 0xFF;
            int entry = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
            locations[i] = new Location(entry >>> 8, entry & 0xFF);
        }
        return locations;
    }

    /**
     * 整文件读入内存后按 location 表随机寻址。
     * location 表不按区块 index 有序，因此不能顺序流式读取。
     */
    public static Map<Integer, byte[]> readChunks(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        Location[] locations = readLocations(data);
        Map<Integer, byte[]> chunks = new TreeMap<>();

        for (int i = 0; i < LOCATION_COUNT; i++) {
            Location location = locations[i];
            if (!location.isPresent() || location.isExternal()) {
                continue;
            }
            long position = (long) location.offset * SECTOR_BYTES;
            byte[] record = readChunkRecord(data, position);
            if (record != null) {
                chunks.put(i, record);
            }
        }
        return chunks;
    }

    private static byte[] readChunkRecord(byte[] data, long position) throws IOException {
        int pos = (int) position;
        if (pos < 0 || pos + 4 > data.length) {
            throw new EOFException("区域文件意外结束");
        }

        int length = ((data[pos] & 0xFF) << 24)
                | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8)
                | (data[pos + 3] & 0xFF);

        // 前 4 字节是载荷长度（含压缩类型字节）
        if (length < 1 || length > MAX_CHUNK_RECORD_BYTES - 4) {
            throw new IOException("非法的区块记录长度: " + length);
        }
        if (pos + 4L + length > data.length) {
            throw new EOFException("区块记录被截断");
        }

        byte[] record = new byte[4 + length];
        System.arraycopy(data, pos, record, 0, 4 + length);
        return record;
    }

    /**
     * 用区块记录的重建整个 region 文件：按 4 KiB 扇区对齐写入，重建 location 表。
     */
    public static void write(Path file, Map<Integer, byte[]> records) throws IOException {
        byte[] header = new byte[HEADER_BYTES];
        long position = HEADER_BYTES;

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
            out.write(header);

            for (int i = 0; i < LOCATION_COUNT; i++) {
                byte[] record = records.get(i);
                if (record == null || record.length < 5) {
                    continue;
                }

                long recordLength = ((long) (record[0] & 0xFF) << 24)
                        | ((record[1] & 0xFF) << 16)
                        | ((record[2] & 0xFF) << 8)
                        | (record[3] & 0xFF);
                if (recordLength != record.length - 4L || recordLength > MAX_CHUNK_RECORD_BYTES - 4L) {
                    throw new IOException("区块 " + i + " 的记录长度非法");
                }

                long offset = ((position + SECTOR_BYTES - 1) / SECTOR_BYTES) * SECTOR_BYTES;
                int sectorCount = (int) Math.max(1, (record.length + (long) SECTOR_BYTES - 1) / SECTOR_BYTES);

                if (offset / SECTOR_BYTES > 0xFFFFFF || sectorCount > 255) {
                    throw new IOException("区块 " + i + " 过大，无法写入区域文件");
                }

                while (position < offset) {
                    out.write(0);
                    position++;
                }
                out.write(record);
                position += record.length;

                int entry = (int) ((offset / SECTOR_BYTES) << 8) | sectorCount;
                header[i * 4] = (byte) (entry >>> 24);
                header[i * 4 + 1] = (byte) (entry >>> 16);
                header[i * 4 + 2] = (byte) (entry >>> 8);
                header[i * 4 + 3] = (byte) entry;
            }
            out.flush();
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(header);
            channel.position(0);
            // FileChannel.write 不保证一次写完整个 buffer
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    public static boolean hasExternalChunks(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        for (Location location : readLocations(data)) {
            if (location.isExternal()) {
                return true;
            }
        }
        return false;
    }

    public static String hashBytes(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return toHex(digest.digest(bytes));
    }

    public static String hashFile(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}
