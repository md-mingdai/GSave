package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.mca.RawMca;
import com.mingdai.savegit.util.L10n;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * 从快照 manifest 与 blob 重建世界文件。调用前世界与集成服务端必须已完全卸载。
 */
public final class WorldRestorer {
    private final SnapshotRepository repo;
    private final Path worldRoot;

    public record RestoreFilter(boolean region, boolean entities, boolean poi, boolean whole) {
        public static final RestoreFilter ALL = new RestoreFilter(true, true, true, true);

        public boolean accepts(Manifest.FileEntry entry) {
            if (entry.isMca()) {
                String type = entry.mcaType == null ? "" : entry.mcaType;
                return switch (type) {
                    case "region" -> this.region;
                    case "entities" -> this.entities;
                    case "poi" -> this.poi;
                    default -> this.whole;
                };
            }
            return this.whole;
        }
    }

    public WorldRestorer(SnapshotRepository repo, Path worldRoot) {
        this.repo = repo;
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
    }

    public void restore(Commit commit) throws IOException {
        restore(commit, RestoreFilter.ALL);
    }

    public void restore(Commit commit, RestoreFilter filter) throws IOException {
        if (this.repo.isReadOnly()) {
            throw new IOException(L10n.tr("gsave.error.repo_too_new",
                    String.valueOf(this.repo.getFormatVersion())));
        }
        try {
            Manifest manifest = repo.getManifest(commit.manifestId);
            if (manifest == null) {
                throw new IOException(L10n.tr("gsave.error.manifest_missing", commit.manifestId));
            }

            java.util.List<Manifest.FileEntry> targets = new java.util.ArrayList<>();
            for (Manifest.FileEntry entry : manifest.getAllEntries()) {
                if (filter == null || filter.accepts(entry)) {
                    targets.add(entry);
                }
            }

            RestoreProgress.begin(targets.size());
            int index = 0;

            for (Manifest.FileEntry entry : targets) {
                if (entry.path == null || entry.path.isEmpty()) {
                    throw new IOException(L10n.tr("gsave.error.empty_path"));
                }
                RestoreProgress.startFile(index, entry.path);

                Path target = worldRoot.resolve(entry.path).normalize();
                if (!target.startsWith(worldRoot)) {
                    throw new IOException(L10n.tr("gsave.error.illegal_path", entry.path));
                }

                if (entry.isMca()) {
                    restoreMcaFile(entry, target);
                } else if (entry.isWhole()) {
                    restoreWholeFile(entry, target);
                }
                RestoreProgress.completeFile();
                index++;
            }
            RestoreProgress.finish();
        } finally {
            repo.getBlobStore().close();
        }
    }

    private void restoreMcaFile(Manifest.FileEntry entry, Path target) throws IOException {
        Map<Integer, byte[]> records = new TreeMap<>();
        for (Map.Entry<Integer, String> chunk : entry.chunks.entrySet()) {
            if (chunk.getValue() == null) {
                continue;
            }
            records.put(chunk.getKey(), repo.getBlobStore().load(chunk.getValue()));
        }
        // location 表由 RawMca.write 按区块记录重建
        RawMca.write(target, records);
    }

    private void restoreWholeFile(Manifest.FileEntry entry, Path target) throws IOException {
        if (entry.hash == null) {
            throw new IOException(L10n.tr("gsave.error.missing_hash", entry.path));
        }
        repo.getBlobStore().copyTo(entry.hash, target);
    }
}
