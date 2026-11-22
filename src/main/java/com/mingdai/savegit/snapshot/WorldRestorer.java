package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.mca.RawMca;
import com.mingdai.savegit.util.L10n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 从快照 manifest 与 blob 重建世界文件。调用前世界与集成服务端必须已完全卸载。
 */
public final class WorldRestorer {
    private final SnapshotRepository repo;
    private final Path worldRoot;

    public record RestoreFilter(boolean region, boolean entities, boolean poi, boolean whole,
                                boolean datapacks) {
        public static final RestoreFilter ALL = new RestoreFilter(true, true, true, true, true);

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
            boolean datapack = entry.path != null && entry.path.startsWith(WorldFileGroups.DATAPACKS_PREFIX);
            return datapack ? this.datapacks : this.whole;
        }
    }

    public WorldRestorer(SnapshotRepository repo, Path worldRoot) {
        this.repo = repo;
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
    }

    public void restore(Commit commit) throws IOException {
        restore(commit, RestoreFilter.ALL, null, false);
    }

    public void restore(Commit commit, RestoreFilter filter) throws IOException {
        restore(commit, filter, null, false);
    }

    public void restore(Commit commit, RestoreFilter filter, ChunkSelection selection) throws IOException {
        restore(commit, filter, selection, false);
    }

    /**
     * @param selection      非空时只恢复选中区块；其余区块保留世界当前状态。
     * @param deleteNewFiles Beta：删除快照中不存在、且属于所选分组的其他世界文件（不含区块与 gsave 数据）。
     */
    public void restore(Commit commit, RestoreFilter filter, ChunkSelection selection,
                        boolean deleteNewFiles) throws IOException {
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
            java.util.List<String> pendingLogs = new java.util.ArrayList<>();
            for (Manifest.FileEntry entry : manifest.getAllEntries()) {
                if (filter != null && !filter.accepts(entry)) {
                    continue;
                }
                if (selection != null) {
                    if (entry.isMca()) {
                        if (!selection.intersects(entry)) {
                            continue;
                        }
                    } else {
                        // 整文件存储的区域文件（含外部 .mcc 或空占位）无法按区块恢复
                        if (entry.path != null && entry.path.endsWith(".mca")) {
                            pendingLogs.add(L10n.tr("gsave.progress.log.skip_whole", entry.path));
                        }
                        continue;
                    }
                }
                targets.add(entry);
            }

            RestoreProgress.begin(targets.size());
            for (String line : pendingLogs) {
                RestoreProgress.addLog(line);
            }
            if (selection != null) {
                RestoreProgress.addLog(L10n.tr("gsave.progress.log.chunks",
                        String.valueOf(selection.size()), selection.dimension()));
            }
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
                    restoreMcaFile(entry, target, selection);
                } else if (entry.isWhole()) {
                    restoreWholeFile(entry, target);
                }
                RestoreProgress.completeFile();
                index++;
            }
            if (deleteNewFiles && selection == null) {
                deleteExtraFiles(manifest, filter);
            }
            RestoreProgress.finish();
        } finally {
            repo.getBlobStore().close();
        }
    }

    /**
     * Beta：删除快照中不存在的其他世界文件。只处理所选分组中的普通文件，
     * 永不删除区块文件与 gsave 数据；删除会写入恢复日志。
     */
    private void deleteExtraFiles(Manifest manifest, RestoreFilter filter) throws IOException {
        Set<String> known = manifest.files.keySet();
        List<Path> toDelete = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(this.worldRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .forEach(path -> {
                        String relative = relativePath(path);
                        String lower = relative.toLowerCase(java.util.Locale.ROOT);
                        if (lower.endsWith(".mca") || lower.endsWith(".mcc")) {
                            return;
                        }
                        if (WorldFileGroups.isExcluded(relative) || known.contains(relative)) {
                            return;
                        }
                        boolean datapack = WorldFileGroups.isDatapack(relative);
                        if (datapack && !filter.datapacks()) {
                            return;
                        }
                        if (!datapack && !filter.whole()) {
                            return;
                        }
                        toDelete.add(path);
                    });
        }

        for (Path path : toDelete) {
            String relative = relativePath(path);
            if (Files.deleteIfExists(path)) {
                RestoreProgress.addLog(L10n.tr("gsave.progress.log.deleted_new", relative));
            }
        }
    }

    private String relativePath(Path path) {
        return this.worldRoot.relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private void restoreMcaFile(Manifest.FileEntry entry, Path target, ChunkSelection selection)
            throws IOException {
        if (selection == null) {
            Map<Integer, byte[]> records = new TreeMap<>();
            for (Map.Entry<Integer, String> chunk : entry.chunks.entrySet()) {
                if (chunk.getValue() == null) {
                    continue;
                }
                records.put(chunk.getKey(), repo.getBlobStore().load(chunk.getValue()));
            }
            // location 表由 RawMca.write 按区块记录重建
            RawMca.write(target, records);
            return;
        }

        // 只覆盖选中区块，其余区块保留世界当前内容
        Map<Integer, byte[]> merged = new TreeMap<>();
        if (Files.isRegularFile(target)) {
            if (RawMca.hasExternalChunks(target)) {
                RestoreProgress.addLog(L10n.tr("gsave.progress.log.skip_external", entry.path));
                return;
            }
            merged.putAll(RawMca.readChunks(target));
        }

        boolean changed = false;
        for (Map.Entry<Integer, String> chunk : entry.chunks.entrySet()) {
            if (chunk.getValue() == null || !selection.matches(entry, chunk.getKey())) {
                continue;
            }
            merged.put(chunk.getKey(), repo.getBlobStore().load(chunk.getValue()));
            changed = true;
        }
        if (changed) {
            RawMca.write(target, merged);
        }
    }

    private void restoreWholeFile(Manifest.FileEntry entry, Path target) throws IOException {
        if (entry.hash == null) {
            throw new IOException(L10n.tr("gsave.error.missing_hash", entry.path));
        }
        repo.getBlobStore().copyTo(entry.hash, target);
    }
}
