package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.mca.RawMca;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * SHA-256 内容寻址的 blob 存储。<哈希前 2 位>/<完整哈希>，相同内容自动去重。
 */
public final class BlobStore {
    private final Path blobsDir;

    public BlobStore(Path blobsDir) {
        this.blobsDir = blobsDir;
    }

    public Path getBlobsDir() {
        return blobsDir;
    }

    public String store(byte[] bytes) {
        String hash = RawMca.hashBytes(bytes);
        return store(bytes, hash);
    }

    public String store(byte[] bytes, String hash) {
        Path blobPath = blobPath(hash);
        if (!Files.exists(blobPath)) {
            try {
                Files.createDirectories(blobPath.getParent());
                Files.write(blobPath, bytes);
            } catch (IOException e) {
                throw new RuntimeException("写入 blob 失败: " + hash, e);
            }
        }
        return hash;
    }

    public String store(Path file) throws IOException {
        String hash = RawMca.hashFile(file);
        Path blobPath = blobPath(hash);
        if (!Files.exists(blobPath)) {
            Files.createDirectories(blobPath.getParent());
            copyFile(file, blobPath);
        }
        return hash;
    }

    public byte[] load(String hash) throws IOException {
        return Files.readAllBytes(blobPath(hash));
    }

    public void copyTo(String hash, Path target) throws IOException {
        Path source = blobPath(hash);
        if (!Files.exists(source)) {
            throw new IOException("blob 不存在: " + hash);
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean exists(String hash) {
        return Files.exists(blobPath(hash));
    }

    public Path blobPath(String hash) {
        if (hash == null || hash.length() < 2) {
            throw new IllegalArgumentException("哈希值过短: " + hash);
        }
        return blobsDir.resolve(hash.substring(0, 2)).resolve(hash);
    }

    private static void copyFile(Path source, Path target) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }
}
