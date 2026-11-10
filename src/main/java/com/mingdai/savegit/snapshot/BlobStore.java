package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.mca.RawMca;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SHA-256 内容寻址的 blob 存储。
 * <p>
 * 新数据按 Git packfile 思路追加写入 {@code savegit/packs/pack-XXXXXX.pack},
 * 每个 pack 配一个 {@code .idx} 记录 hash -> 偏移/长度，避免每个区块一个文件。
 * 旧版 {@code savegit/blobs/<前2位>/<hash>} 会在首次写入时自动迁移进 pack 并删除散文件。
 */
public final class BlobStore implements AutoCloseable {
    private static final int MAX_PACK_BYTES = 64 * 1024 * 1024;
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PACK_PATTERN = Pattern.compile("pack-(\\d+)\\.(?:pack|idx)");

    private final Path packsDir;
    private final Path blobsDir;
    private final Path legacyMarker;
    private final Map<String, BlobLocation> index = new HashMap<>();
    private final Map<Integer, FileChannel> readChannels = new HashMap<>();

    private boolean loaded;
    private boolean migrated;
    private int nextPackId = 1;
    private PackWriter currentPack;

    public BlobStore(Path saveGitDir) {
        this.packsDir = saveGitDir.resolve("packs");
        this.blobsDir = saveGitDir.resolve("blobs");
        this.legacyMarker = packsDir.resolve(".legacy-migrated");
    }

    public String store(byte[] bytes) {
        String hash = RawMca.hashBytes(bytes);
        return store(bytes, hash);
    }

    public synchronized String store(byte[] bytes, String hash) {
        try {
            ensureMigrated();
            if (hasBlob(hash)) {
                return hash;
            }
            PackWriter writer = packWriter();
            if (!writer.isEmpty() && writer.position + 8 + bytes.length > MAX_PACK_BYTES) {
                rotatePack();
                writer = packWriter();
            }
            writer.append(hash, bytes);
            return hash;
        } catch (IOException e) {
            throw new RuntimeException("写入 blob 失败: " + hash, e);
        }
    }

    public synchronized String store(Path file) throws IOException {
        ensureMigrated();
        String hash = RawMca.hashFile(file);
        if (hasBlob(hash)) {
            return hash;
        }

        long length = Files.size(file);
        PackWriter writer = packWriter();
        if (!writer.isEmpty() && writer.position + 8 + length > MAX_PACK_BYTES) {
            rotatePack();
            writer = packWriter();
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            writer.appendStream(hash, in, length, sha256());
        }
        return hash;
    }

    public synchronized byte[] load(String hash) throws IOException {
        ensureLoaded();
        BlobLocation location = index.get(hash);
        if (location != null) {
            return loadFromPack(location);
        }

        Path legacy = legacyPath(hash);
        if (Files.isRegularFile(legacy)) {
            return Files.readAllBytes(legacy);
        }
        throw new IOException("blob 不存在: " + hash);
    }

    public synchronized void copyTo(String hash, Path target) throws IOException {
        ensureLoaded();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        BlobLocation location = index.get(hash);
        if (location != null) {
            copyFromPack(location, target);
            return;
        }

        Path legacy = legacyPath(hash);
        if (!Files.isRegularFile(legacy)) {
            throw new IOException("blob 不存在: " + hash);
        }
        Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized boolean exists(String hash) {
        try {
            ensureLoaded();
        } catch (IOException e) {
            return false;
        }
        return hasBlob(hash);
    }

    /** 当前存储中所有 blob 的哈希（pack + 旧版散文件）。 */
    public synchronized java.util.Set<String> listHashes() {
        try {
            ensureLoaded();
        } catch (IOException e) {
            return java.util.Set.of();
        }
        java.util.Set<String> hashes = new java.util.HashSet<>(index.keySet());
        hashes.addAll(listLegacyHashes());
        return hashes;
    }

    public synchronized long sizeOf(String hash) {
        try {
            ensureLoaded();
        } catch (IOException e) {
            return 0L;
        }
        BlobLocation location = index.get(hash);
        if (location != null) {
            return location.length();
        }
        try {
            return Files.size(legacyPath(hash));
        } catch (IOException e) {
            return 0L;
        }
    }

    public synchronized long totalPackedBytes() {
        try {
            ensureLoaded();
        } catch (IOException e) {
            return 0L;
        }
        long total = 0L;
        for (BlobLocation location : index.values()) {
            total += location.length();
        }
        return total;
    }

    /**
     * 只保留 keep 中的 blob：写入新的 pack 编号并逐个校验 SHA-256，
     * 全部完成后再删除旧 pack，中断时最多留下重复数据，可再次执行收敛。
     */
    public synchronized RepackResult repack(java.util.Set<String> keep) throws IOException {
        ensureMigrated();
        if (currentPack != null) {
            currentPack.finish();
            currentPack = null;
        }

        java.util.List<Integer> oldPackIds = listPackIds();
        java.util.Map<String, BlobLocation> snapshot = new java.util.HashMap<>(index);

        // 任何被引用的 blob 缺失时中止，避免把损坏固化成永久丢失
        for (String hash : keep) {
            if (!snapshot.containsKey(hash) && !Files.isRegularFile(legacyPath(hash))) {
                throw new IOException("缺少被引用的 blob: " + hash);
            }
        }

        long totalBefore = 0L;
        for (BlobLocation location : snapshot.values()) {
            totalBefore += location.length();
        }

        long keptBytes = 0L;
        int kept = 0;
        int removed = 0;
        int newPacks = 0;
        java.util.List<Integer> writtenPackIds = new java.util.ArrayList<>();

        PackWriter writer = null;
        try {
            for (java.util.Map.Entry<String, BlobLocation> entry : snapshot.entrySet()) {
                String hash = entry.getKey();
                BlobLocation location = entry.getValue();
                if (!keep.contains(hash)) {
                    removed++;
                    continue;
                }
                if (writer == null
                        || (!writer.isEmpty()
                        && writer.position + 8 + location.length() > MAX_PACK_BYTES)) {
                    if (writer != null) {
                        writer.finish();
                        writtenPackIds.add(writer.id);
                        newPacks++;
                    }
                    writer = new PackWriter(nextPackId++);
                }
                writer.appendFromChannel(readChannel(location.packId()),
                        location.offset(), location.length(), hash);
                keptBytes += location.length();
                kept++;
            }
            if (writer != null) {
                writer.finish();
                writtenPackIds.add(writer.id);
                newPacks++;
                writer = null;
            }
        } catch (IOException e) {
            if (writer != null) {
                try {
                    writer.channel.close();
                } catch (IOException ignored) {
                }
                deleteQuietly(packPath(writer.id));
                deleteQuietly(packIndexPath(writer.id));
            }
            for (int id : writtenPackIds) {
                deleteQuietly(packPath(id));
                deleteQuietly(packIndexPath(id));
            }
            throw e;
        }

        closeReadChannels();
        for (int id : oldPackIds) {
            deleteQuietly(packPath(id));
            deleteQuietly(packIndexPath(id));
        }
        reloadIndex();

        // 旧版散文件：保留集合中的并入 pack，其余删除
        for (String hash : listLegacyHashes()) {
            Path legacy = legacyPath(hash);
            if (!keep.contains(hash)) {
                deleteQuietly(legacy);
                removed++;
                continue;
            }
            if (index.containsKey(hash)) {
                deleteQuietly(legacy);
                continue;
            }
            if (packLegacyFile(legacy, hash)) {
                deleteQuietly(legacy);
                kept++;
            }
        }
        flush();
        deleteEmptyDirectories();

        long freed = Math.max(0L, totalBefore - keptBytes);
        return new RepackResult(freed, kept, removed, newPacks);
    }

    /** 磁盘上仍有但索引未加载的旧版散文件哈希。 */
    private java.util.Set<String> listLegacyHashes() {
        java.util.Set<String> hashes = new java.util.HashSet<>();
        if (!Files.isDirectory(blobsDir)) {
            return hashes;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(blobsDir)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                    for (Path file : files) {
                        String name = file.getFileName().toString();
                        if (HASH_PATTERN.matcher(name).matches()) {
                            hashes.add(name);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return hashes;
    }

    private boolean packLegacyFile(Path file, String hash) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            PackWriter writer = packWriter();
            long length = Files.size(file);
            if (!writer.isEmpty() && writer.position + 8 + length > MAX_PACK_BYTES) {
                rotatePack();
                writer = packWriter();
            }
            writer.appendStream(hash, in, length, sha256());
            return true;
        } catch (IOException e) {
            SaveGitMod.LOGGER.warn("迁移旧 blob 失败: {}", hash, e);
            return false;
        }
    }

    private void reloadIndex() throws IOException {
        closeReadChannels();
        index.clear();
        loaded = false;
        ensureLoaded();
    }

    private void closeReadChannels() {
        for (FileChannel channel : readChannels.values()) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
        readChannels.clear();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public record RepackResult(long freedBytes, int keptBlobs, int removedBlobs, int newPacks) {
    }

    /** 把当前 pack 的数据和索引落盘。 */
    public synchronized void flush() throws IOException {
        if (currentPack != null) {
            currentPack.flushData();
            currentPack.writeIndex();
        }
    }

    @Override
    public synchronized void close() {
        try {
            flush();
        } catch (IOException e) {
            SaveGitMod.LOGGER.error("关闭 blob pack 失败", e);
        } finally {
            if (currentPack != null) {
                try {
                    currentPack.channel.close();
                } catch (IOException e) {
                    SaveGitMod.LOGGER.error("关闭 blob pack 文件失败", e);
                }
                currentPack = null;
            }
            for (FileChannel channel : readChannels.values()) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
            readChannels.clear();
        }
    }

    private boolean hasBlob(String hash) {
        return index.containsKey(hash) || Files.isRegularFile(legacyPath(hash));
    }

    private PackWriter packWriter() throws IOException {
        if (currentPack == null) {
            Files.createDirectories(packsDir);
            currentPack = new PackWriter(nextPackId++);
        }
        return currentPack;
    }

    private void rotatePack() throws IOException {
        if (currentPack != null) {
            currentPack.finish();
            currentPack = null;
        }
    }

    private void ensureLoaded() throws IOException {
        if (loaded) {
            return;
        }
        Files.createDirectories(packsDir);

        List<Integer> packIds = listPackIds();
        for (int packId : packIds) {
            Path packFile = packPath(packId);
            Path indexFile = packIndexPath(packId);
            if (!Files.isRegularFile(packFile) || !Files.isRegularFile(indexFile)) {
                continue;
            }
            long packSize = Files.size(packFile);
            try (var reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split(" ");
                    if (parts.length != 3) {
                        continue;
                    }
                    String hash = parts[0];
                    if (!HASH_PATTERN.matcher(hash).matches()) {
                        continue;
                    }
                    try {
                        long offset = Long.parseLong(parts[1]);
                        long length = Long.parseLong(parts[2]);
                        if (length < 0 || offset < 0 || offset + length > packSize) {
                            continue;
                        }
                        index.putIfAbsent(hash, new BlobLocation(packId, offset, length));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        int maxId = 0;
        for (int packId : packIds) {
            maxId = Math.max(maxId, packId);
        }
        nextPackId = maxId + 1;
        loaded = true;
    }

    private List<Integer> listPackIds() throws IOException {
        List<Integer> ids = new ArrayList<>();
        if (!Files.isDirectory(packsDir)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packsDir, "pack-*")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    continue;
                }
                Matcher matcher = PACK_PATTERN.matcher(name);
                if (matcher.matches()) {
                    int id = Integer.parseInt(matcher.group(1));
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private void ensureMigrated() throws IOException {
        ensureLoaded();
        if (migrated) {
            return;
        }
        migrated = true;

        if (Files.isRegularFile(legacyMarker)) {
            return;
        }
        if (!Files.isDirectory(blobsDir)) {
            writeMarker();
            return;
        }

        int packed = 0;
        List<Path> toDelete = new ArrayList<>();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(blobsDir)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                String prefix = dir.getFileName().toString();
                if (prefix.length() != 2) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                    for (Path file : files) {
                        if (!Files.isRegularFile(file)) {
                            continue;
                        }
                        String hash = file.getFileName().toString();
                        if (!HASH_PATTERN.matcher(hash).matches() || !hash.startsWith(prefix)) {
                            continue;
                        }
                        if (index.containsKey(hash)) {
                            toDelete.add(file);
                            continue;
                        }
                        try {
                            appendLegacyFile(file, hash);
                            toDelete.add(file);
                            packed++;
                        } catch (Throwable t) {
                            SaveGitMod.LOGGER.warn("迁移旧 blob 失败: {}", hash, t);
                        }
                    }
                }
            }
        }

        flush();
        for (Path file : toDelete) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                SaveGitMod.LOGGER.warn("删除旧 blob 失败: {}", file, e);
            }
        }
        deleteEmptyDirectories();
        writeMarker();
        if (packed > 0) {
            SaveGitMod.LOGGER.info("已把 {} 个旧 blob 迁移进 pack 存储", packed);
        }
    }

    private void appendLegacyFile(Path file, String hash) throws IOException {
        long length = Files.size(file);
        PackWriter writer = packWriter();
        if (!writer.isEmpty() && writer.position + 8 + length > MAX_PACK_BYTES) {
            rotatePack();
            writer = packWriter();
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            writer.appendStream(hash, in, length, sha256());
        }
    }

    private void deleteEmptyDirectories() {
        if (!Files.isDirectory(blobsDir)) {
            return;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(blobsDir)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                    if (!files.iterator().hasNext()) {
                        Files.deleteIfExists(dir);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void writeMarker() {
        try {
            Files.createDirectories(packsDir);
            Path temp = legacyMarker.resolveSibling(legacyMarker.getFileName() + ".tmp");
            Files.writeString(temp, "ok\n", StandardCharsets.UTF_8);
            moveAtomic(temp, legacyMarker);
        } catch (IOException e) {
            SaveGitMod.LOGGER.warn("写入 blob 迁移标记失败", e);
        }
    }

    private byte[] loadFromPack(BlobLocation location) throws IOException {
        if (location.length > Integer.MAX_VALUE - 8) {
            throw new IOException("blob 过大: " + location.length);
        }
        if (currentPack != null && currentPack.id == location.packId) {
            currentPack.flushData();
        }
        FileChannel channel = readChannel(location.packId);
        ByteBuffer buffer = ByteBuffer.allocate((int) location.length);
        long position = location.offset;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new EOFException("pack 数据被截断");
            }
            position += read;
        }
        return buffer.array();
    }

    private void copyFromPack(BlobLocation location, Path target) throws IOException {
        if (currentPack != null && currentPack.id == location.packId) {
            currentPack.flushData();
        }
        FileChannel channel = readChannel(location.packId);
        try (OutputStream out = Files.newOutputStream(target)) {
            long position = location.offset;
            long remaining = location.length;
            byte[] buffer = new byte[COPY_BUFFER_SIZE];

            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, toRead);
                int read = channel.read(byteBuffer, position);
                if (read < 0) {
                    throw new EOFException("pack 数据被截断");
                }
                out.write(buffer, 0, read);
                position += read;
                remaining -= read;
            }
        }
    }

    private FileChannel readChannel(int packId) throws IOException {
        FileChannel channel = readChannels.get(packId);
        if (channel == null) {
            channel = FileChannel.open(packPath(packId), StandardOpenOption.READ);
            readChannels.put(packId, channel);
        }
        return channel;
    }

    private Path packPath(int packId) {
        return packsDir.resolve(String.format(Locale.ROOT, "pack-%06d.pack", packId));
    }

    private Path packIndexPath(int packId) {
        return packsDir.resolve(String.format(Locale.ROOT, "pack-%06d.idx", packId));
    }

    private Path legacyPath(String hash) {
        if (hash == null || hash.length() < 2) {
            throw new IllegalArgumentException("哈希值过短: " + hash);
        }
        return blobsDir.resolve(hash.substring(0, 2)).resolve(hash);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void moveAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record BlobLocation(int packId, long offset, long length) {
    }

    private final class PackWriter {
        private final int id;
        private final FileChannel channel;
        private final TreeMap<String, BlobLocation> entries = new TreeMap<>();
        private long position;

        private PackWriter(int id) throws IOException {
            this.id = id;
            this.channel = FileChannel.open(packPath(id),
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.position = channel.size();
            channel.position(position);
        }

        private boolean isEmpty() {
            return entries.isEmpty();
        }

        private void append(String hash, byte[] data) throws IOException {
            long start = position;
            try {
                writeLong(data.length);
                long offset = position;
                writeFully(ByteBuffer.wrap(data));
                position += data.length;

                BlobLocation location = new BlobLocation(id, offset, data.length);
                entries.put(hash, location);
                index.put(hash, location);
            } catch (IOException e) {
                channel.truncate(start);
                channel.position(start);
                position = start;
                throw e;
            }
        }

        private void appendStream(String hash, InputStream in, long length, MessageDigest digest)
                throws IOException {
            long start = position;
            try {
                writeLong(length);
                long offset = position;
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                long remaining = length;

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = in.read(buffer, 0, toRead);
                    if (read < 0) {
                        throw new IOException("文件在快照期间变短: " + hash);
                    }
                    digest.update(buffer, 0, read);
                    writeFully(ByteBuffer.wrap(buffer, 0, read));
                    position += read;
                    remaining -= read;
                }
                if (in.read() != -1) {
                    throw new IOException("文件在快照期间变长: " + hash);
                }

                String actual = toHex(digest.digest());
                if (!actual.equals(hash)) {
                    throw new IOException("文件在快照期间发生变化: " + hash);
                }

                BlobLocation location = new BlobLocation(id, offset, length);
                entries.put(hash, location);
                index.put(hash, location);
            } catch (IOException e) {
                channel.truncate(start);
                channel.position(start);
                position = start;
                throw e;
            }
        }

        private void appendFromChannel(FileChannel source, long sourceOffset,
                                       long length, String hash) throws IOException {
            long start = position;
            try {
                writeLong(length);
                long dataOffset = position;
                long offset = sourceOffset;
                long remaining = length;
                MessageDigest digest = sha256();
                byte[] buffer = new byte[COPY_BUFFER_SIZE];

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, toRead);
                    int read = source.read(byteBuffer, offset);
                    if (read < 0) {
                        throw new EOFException("pack 数据被截断");
                    }
                    digest.update(buffer, 0, read);
                    writeFully(ByteBuffer.wrap(buffer, 0, read));
                    position += read;
                    offset += read;
                    remaining -= read;
                }

                String actual = toHex(digest.digest());
                if (!actual.equals(hash)) {
                    throw new IOException("blob 校验失败: " + hash);
                }

                BlobLocation location = new BlobLocation(id, dataOffset, length);
                entries.put(hash, location);
            } catch (IOException e) {
                channel.truncate(start);
                channel.position(start);
                position = start;
                throw e;
            }
        }

        private void writeLong(long value) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putLong(value);
            buffer.flip();
            writeFully(buffer);
            position += 8;
        }

        private void writeFully(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }

        private void flushData() throws IOException {
            channel.force(false);
        }

        private void writeIndex() throws IOException {
            Path indexFile = packIndexPath(id);
            Path temp = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");
            StringBuilder builder = new StringBuilder(entries.size() * 80);
            for (Map.Entry<String, BlobLocation> entry : entries.entrySet()) {
                BlobLocation location = entry.getValue();
                builder.append(entry.getKey()).append(' ')
                        .append(location.offset()).append(' ')
                        .append(location.length()).append('\n');
            }
            Files.writeString(temp, builder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveAtomic(temp, indexFile);
        }

        private void finish() throws IOException {
            try {
                channel.force(true);
                writeIndex();
            } finally {
                channel.close();
            }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0x0F, 16));
            builder.append(Character.forDigit(value & 0x0F, 16));
        }
        return builder.toString();
    }
}
