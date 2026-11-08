package com.mingdai.savegit.snapshot;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 生成两个 manifest 的 git 式差异，处理 whole 与 mca 表示之间的翻转。
 */
public final class PatchGenerator {
    private PatchGenerator() {
    }

    public static Patch generate(String commitId, String parentCommitId,
                                 Manifest oldManifest, Manifest newManifest) {
        Patch patch = new Patch();
        patch.commitId = commitId;
        patch.parentCommitId = parentCommitId;

        Manifest oldM = oldManifest == null ? new Manifest() : oldManifest;
        Manifest newM = newManifest == null ? new Manifest() : newManifest;

        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(oldM.files.keySet());
        paths.addAll(newM.files.keySet());

        for (String path : paths) {
            Manifest.FileEntry oldEntry = oldM.getEntry(path);
            Manifest.FileEntry newEntry = newM.getEntry(path);

            if (oldEntry == null && newEntry == null) {
                continue;
            }
            if (oldEntry == null) {
                patch.files.put(path, addedFile(newEntry));
                continue;
            }
            if (newEntry == null) {
                patch.files.put(path, removedFile(oldEntry));
                continue;
            }
            if (entriesEqual(oldEntry, newEntry)) {
                continue;
            }
            patch.files.put(path, modifiedFile(oldEntry, newEntry));
        }
        return patch;
    }

    private static Patch.FileChange addedFile(Manifest.FileEntry entry) {
        Patch.FileChange change = new Patch.FileChange();
        change.change = Patch.ChangeType.ADDED;
        change.type = entry.type;
        if (entry.isMca()) {
            for (Map.Entry<Integer, String> chunk : entry.chunks.entrySet()) {
                change.chunks.put(chunk.getKey(),
                        new Patch.ChunkChange(Patch.ChangeType.ADDED, chunk.getValue()));
            }
        } else {
            change.newHash = entry.hash;
        }
        return change;
    }

    private static Patch.FileChange removedFile(Manifest.FileEntry entry) {
        Patch.FileChange change = new Patch.FileChange();
        change.change = Patch.ChangeType.REMOVED;
        change.type = entry.type;
        if (entry.isMca()) {
            for (Integer index : entry.chunks.keySet()) {
                change.chunks.put(index, new Patch.ChunkChange(Patch.ChangeType.REMOVED, null));
            }
        } else {
            change.oldHash = entry.hash;
        }
        return change;
    }

    private static Patch.FileChange modifiedFile(Manifest.FileEntry oldEntry, Manifest.FileEntry newEntry) {
        Patch.FileChange change = new Patch.FileChange();
        change.change = Patch.ChangeType.MODIFIED;
        change.type = newEntry.type;

        if (newEntry.isWhole()) {
            change.oldHash = oldEntry.hash;
            change.newHash = newEntry.hash;
            return change;
        }

        // 新表示是 mca
        if (!oldEntry.isMca()) {
            // whole -> mca：视为整个文件重建，所有区块新增
            change.oldHash = oldEntry.hash;
            for (Map.Entry<Integer, String> chunk : newEntry.chunks.entrySet()) {
                change.chunks.put(chunk.getKey(),
                        new Patch.ChunkChange(Patch.ChangeType.ADDED, chunk.getValue()));
            }
            return change;
        }

        Map<Integer, String> allChunks = new TreeMap<>();
        allChunks.putAll(oldEntry.chunks);
        allChunks.putAll(newEntry.chunks);

        for (Map.Entry<Integer, String> chunk : allChunks.entrySet()) {
            int index = chunk.getKey();
            String oldHash = oldEntry.chunks.get(index);
            String newHash = newEntry.chunks.get(index);

            if (oldHash == null) {
                change.chunks.put(index, new Patch.ChunkChange(Patch.ChangeType.ADDED, newHash));
            } else if (newHash == null) {
                change.chunks.put(index, new Patch.ChunkChange(Patch.ChangeType.REMOVED, null));
            } else if (!oldHash.equals(newHash)) {
                change.chunks.put(index, new Patch.ChunkChange(Patch.ChangeType.MODIFIED, newHash));
            }
        }
        return change;
    }

    private static boolean entriesEqual(Manifest.FileEntry a, Manifest.FileEntry b) {
        if (a.type == null || b.type == null) {
            return false;
        }
        if (a.isWhole() && b.isWhole()) {
            return Objects.equals(a.hash, b.hash);
        }
        // 在 whole 与 mca 之间翻转（如空占位 region 长出真实区块）视为变化
        if (!a.isMca() || !b.isMca()) {
            return false;
        }
        if (!Objects.equals(a.dimension, b.dimension)) {
            return false;
        }
        if (!Objects.equals(a.mcaType, b.mcaType)) {
            return false;
        }
        if (a.regionX != b.regionX || a.regionZ != b.regionZ) {
            return false;
        }
        if (a.chunks.size() != b.chunks.size()) {
            return false;
        }
        for (Map.Entry<Integer, String> chunk : a.chunks.entrySet()) {
            String other = b.chunks.get(chunk.getKey());
            if (other == null || !other.equals(chunk.getValue())) {
                return false;
            }
        }
        return true;
    }
}
