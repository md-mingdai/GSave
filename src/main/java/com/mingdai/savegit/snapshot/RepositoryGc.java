package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.util.L10n;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 回收未被任何快照引用的 blob，并清理无主截图/补丁/清单。
 * 只在世界未加载时执行（由离线管理入口触发）。
 */
public final class RepositoryGc {
    public record Result(long freedBytes, int keptBlobs, int removedBlobs,
                         int newPacks, int orphanFiles) {
    }

    private RepositoryGc() {
    }

    public static Result collect(Path worldRoot, BiConsumer<Integer, String> progress)
            throws IOException {
        SnapshotRepository repo = new SnapshotRepository(worldRoot);
        repo.ensureWritableFormat();
        List<Commit> commits = repo.listCommits();

        Set<String> commitIds = new HashSet<>();
        Set<String> manifestIds = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        for (Commit commit : commits) {
            commitIds.add(commit.id);
            manifestIds.add(commit.manifestId);
        }

        progress.accept(5, L10n.tr("gsave.gc.log.scan", String.valueOf(commits.size())));
        int index = 0;
        for (Commit commit : commits) {
            Manifest manifest = repo.getManifest(commit.manifestId);
            if (manifest != null) {
                RepositoryStats.collectHashes(manifest, referenced);
            }
            index++;
            if (index % 50 == 0) {
                progress.accept(5 + 45 * index / Math.max(1, commits.size()),
                        L10n.tr("gsave.gc.log.scan", String.valueOf(index)));
            }
        }

        progress.accept(55, L10n.tr("gsave.gc.log.repack",
                String.valueOf(referenced.size())));
        BlobStore store = repo.getBlobStore();
        BlobStore.RepackResult repack;
        try {
            repack = store.repack(referenced);
        } finally {
            store.close();
        }

        progress.accept(85, L10n.tr("gsave.gc.log.orphans"));
        int orphanFiles = RepositoryHousekeeping.deleteOrphanShots(
                repo.getSaveGitDir(), commitIds)
                + RepositoryHousekeeping.deleteOrphanPatches(
                repo.getSaveGitDir(), commitIds)
                + RepositoryHousekeeping.deleteOrphanManifests(
                repo.getSaveGitDir(), manifestIds)
                + RepositoryHousekeeping.deleteTempFiles(repo.getSaveGitDir());

        progress.accept(100, L10n.tr("gsave.gc.log.done"));
        return new Result(repack.freedBytes(), repack.keptBlobs(), repack.removedBlobs(),
                repack.newPacks(), orphanFiles);
    }
}
