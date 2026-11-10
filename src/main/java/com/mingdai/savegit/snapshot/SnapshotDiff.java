package com.mingdai.savegit.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 两个快照清单的差异汇总（文件级 + 区块级）。
 */
public final class SnapshotDiff {
    public enum Kind {
        ADDED,
        REMOVED,
        MODIFIED
    }

    public record FileDiff(String path, Kind kind, int addedChunks,
                           int removedChunks, int modifiedChunks) {
        public int changeCount() {
            return Math.max(1, this.addedChunks + this.removedChunks + this.modifiedChunks);
        }
    }

    public record Diff(String fromId, String toId,
                       int addedFiles, int removedFiles, int modifiedFiles,
                       int addedChunks, int removedChunks, int modifiedChunks,
                       List<FileDiff> files) {
        public int totalChanges() {
            return this.addedChunks + this.removedChunks + this.modifiedChunks;
        }
    }

    private SnapshotDiff() {
    }

    public static Diff compute(String fromId, String toId, Manifest from, Manifest to) {
        Manifest source = from == null ? new Manifest() : from;
        Manifest target = to == null ? new Manifest() : to;

        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(source.files.keySet());
        paths.addAll(target.files.keySet());

        List<FileDiff> files = new ArrayList<>();
        int addedFiles = 0;
        int removedFiles = 0;
        int modifiedFiles = 0;
        int addedChunks = 0;
        int removedChunks = 0;
        int modifiedChunks = 0;

        for (String path : paths) {
            Manifest.FileEntry oldEntry = source.getEntry(path);
            Manifest.FileEntry newEntry = target.getEntry(path);

            FileDiff diff;
            if (oldEntry == null) {
                diff = new FileDiff(path, Kind.ADDED, chunkCount(newEntry), 0, 0);
                addedFiles++;
            } else if (newEntry == null) {
                diff = new FileDiff(path, Kind.REMOVED, 0, chunkCount(oldEntry), 0);
                removedFiles++;
            } else {
                int[] chunks = compareChunks(oldEntry, newEntry);
                if (chunks[0] == 0 && chunks[1] == 0 && chunks[2] == 0
                        && !wholeFileChanged(oldEntry, newEntry)) {
                    continue;
                }
                Kind kind = Kind.MODIFIED;
                diff = new FileDiff(path, kind, chunks[0], chunks[1], chunks[2]);
                modifiedFiles++;
            }

            addedChunks += diff.addedChunks();
            removedChunks += diff.removedChunks();
            modifiedChunks += diff.modifiedChunks();
            files.add(diff);
        }

        files.sort(Comparator.comparingInt(FileDiff::changeCount).reversed()
                .thenComparing(FileDiff::path));

        return new Diff(fromId, toId, addedFiles, removedFiles, modifiedFiles,
                addedChunks, removedChunks, modifiedChunks, files);
    }

    private static int chunkCount(Manifest.FileEntry entry) {
        return entry.isMca() ? entry.chunks.size() : 1;
    }

    private static boolean wholeFileChanged(Manifest.FileEntry oldEntry, Manifest.FileEntry newEntry) {
        if (oldEntry.isWhole() && newEntry.isWhole()) {
            return !java.util.Objects.equals(oldEntry.hash, newEntry.hash);
        }
        return false;
    }

    private static int[] compareChunks(Manifest.FileEntry oldEntry, Manifest.FileEntry newEntry) {
        if (!oldEntry.isMca() || !newEntry.isMca()) {
            if (oldEntry.isWhole() && newEntry.isWhole()) {
                return new int[]{0, 0, 0};
            }
            return new int[]{chunkCount(newEntry), chunkCount(oldEntry), 0};
        }

        Map<Integer, String> all = new TreeMap<>();
        all.putAll(oldEntry.chunks);
        all.putAll(newEntry.chunks);

        int added = 0;
        int removed = 0;
        int modified = 0;
        for (Map.Entry<Integer, String> chunk : all.entrySet()) {
            String oldHash = oldEntry.chunks.get(chunk.getKey());
            String newHash = newEntry.chunks.get(chunk.getKey());
            if (oldHash == null) {
                added++;
            } else if (newHash == null) {
                removed++;
            } else if (!oldHash.equals(newHash)) {
                modified++;
            }
        }
        return new int[]{added, removed, modified};
    }
}
