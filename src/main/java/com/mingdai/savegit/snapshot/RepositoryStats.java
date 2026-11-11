package com.mingdai.savegit.snapshot;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 仓库占用统计：按类别汇总大小，并区分被引用与可回收的 blob。
 */
public final class RepositoryStats {
    public record Stats(int formatVersion, boolean readOnly, int snapshotCount,
                        long oldestTimestamp, long newestTimestamp,
                        long commitBytes, long manifestBytes, long patchBytes,
                        long packBytes, long shotBytes,
                        long referencedBlobBytes, long unreferencedBlobBytes,
                        int blobCount) {
        public long totalBytes() {
            return this.commitBytes + this.manifestBytes + this.patchBytes
                    + this.packBytes + this.shotBytes;
        }
    }

    private RepositoryStats() {
    }

    public static Stats compute(Path worldRoot) throws IOException {
        SnapshotRepository repo = new SnapshotRepository(worldRoot);
        Path saveGitDir = repo.getSaveGitDir();
        List<Commit> commits = repo.listCommits();

        long oldest = 0L;
        long newest = 0L;
        Set<String> referenced = new HashSet<>();
        for (Commit commit : commits) {
            if (oldest == 0L || commit.timestamp < oldest) {
                oldest = commit.timestamp;
            }
            if (commit.timestamp > newest) {
                newest = commit.timestamp;
            }
            Manifest manifest = repo.getManifest(commit.manifestId);
            if (manifest == null) {
                continue;
            }
            collectHashes(manifest, referenced);
        }

        BlobStore store = repo.getBlobStore();
        long referencedBytes = 0L;
        long unreferencedBytes = 0L;
        int blobCount = 0;
        try {
            for (String hash : store.listHashes()) {
                blobCount++;
                long size = store.sizeOf(hash);
                if (referenced.contains(hash)) {
                    referencedBytes += size;
                } else {
                    unreferencedBytes += size;
                }
            }
        } finally {
            store.close();
        }

        return new Stats(
                repo.getFormatVersion(),
                repo.isReadOnly(),
                commits.size(),
                oldest,
                newest,
                directorySize(saveGitDir.resolve(SnapshotRepository.DIR_SNAPSHOTS), ".json"),
                directorySize(saveGitDir.resolve(SnapshotRepository.DIR_FILELISTS), ".json"),
                directorySize(saveGitDir.resolve(SnapshotRepository.DIR_CHANGES), ".json"),
                directorySize(saveGitDir.resolve(SnapshotRepository.DIR_DATA), null),
                directorySize(saveGitDir.resolve(SnapshotRepository.DIR_SCREENSHOTS), ".png"),
                referencedBytes,
                unreferencedBytes,
                blobCount
        );
    }

    static void collectHashes(Manifest manifest, Set<String> target) {
        for (Manifest.FileEntry entry : manifest.getAllEntries()) {
            if (entry.isWhole() && entry.hash != null) {
                target.add(entry.hash);
            } else if (entry.isMca()) {
                for (String hash : entry.chunks.values()) {
                    if (hash != null) {
                        target.add(hash);
                    }
                }
            }
        }
    }

    private static long directorySize(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        long total = 0L;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (suffix != null && !path.getFileName().toString().endsWith(suffix)) {
                    continue;
                }
                try {
                    total += Files.size(path);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return total;
    }
}
