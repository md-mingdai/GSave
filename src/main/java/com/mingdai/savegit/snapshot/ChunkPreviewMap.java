package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.mca.RawMca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * 恢复预览：按区块比较目标快照与当前世界，得到每个区块将被修改 / 删除 / 保持不变的状态。
 * 只统计将要参与恢复的分组；限区块恢复时只统计半径内的区块。
 */
public final class ChunkPreviewMap {
    public enum State {
        /** 快照内容与当前不同，恢复时会被覆盖。 */
        MODIFIED,
        /** 当前存在但快照中没有，整文件恢复时会被删除。 */
        DELETED,
        /** 快照与当前内容一致。 */
        UNCHANGED;

        /** 合并多个分组的同一区块时取“变化更大”的状态。 */
        public State merge(State other) {
            if (this == MODIFIED || other == MODIFIED) {
                return MODIFIED;
            }
            if (this == DELETED || other == DELETED) {
                return DELETED;
            }
            return UNCHANGED;
        }
    }

    public record ChunkPos(int x, int z) {
    }

    private final Map<String, Map<ChunkPos, State>> dimensions = new HashMap<>();

    public Map<ChunkPos, State> forDimension(String dimension) {
        return this.dimensions.getOrDefault(dimension, Map.of());
    }

    public int count(String dimension, State state) {
        int count = 0;
        for (State value : this.forDimension(dimension).values()) {
            if (value == state) {
                count++;
            }
        }
        return count;
    }

    public int totalChunks() {
        int total = 0;
        for (Map<ChunkPos, State> map : this.dimensions.values()) {
            total += map.size();
        }
        return total;
    }

    private void put(String dimension, ChunkPos pos, State state) {
        Map<ChunkPos, State> map = this.dimensions.computeIfAbsent(dimension, key -> new HashMap<>());
        State existing = map.get(pos);
        map.put(pos, existing == null ? state : existing.merge(state));
    }

    /**
     * @param selection 非空时只统计该选择内的区块，且不会产生 DELETED（未选中的区块不会被改动）。
     */
    public static ChunkPreviewMap compute(Path worldRoot, Manifest manifest,
                                          WorldRestorer.RestoreFilter filter,
                                          ChunkSelection selection) throws IOException {
        ChunkPreviewMap preview = new ChunkPreviewMap();
        if (manifest == null) {
            return preview;
        }
        Path root = worldRoot.toAbsolutePath().normalize();
        for (Manifest.FileEntry entry : manifest.getAllEntries()) {
            if (!entry.isMca() || (filter != null && !filter.accepts(entry))) {
                continue;
            }
            if (selection != null && !selection.intersects(entry)) {
                continue;
            }
            Path file = root.resolve(entry.path).normalize();
            if (!file.startsWith(root)) {
                continue;
            }

            Map<Integer, byte[]> current;
            if (!Files.isRegularFile(file)) {
                // 当前世界没有这个文件:恢复会重新创建其中的区块
                current = Map.of();
            } else if (RawMca.hasExternalChunks(file)) {
                // 含外部 .mcc 的整文件区域无法按区块比较,保守标记为将被修改
                markSnapshotModified(preview, entry, selection);
                continue;
            } else {
                try {
                    current = RawMca.readChunks(file);
                } catch (IOException e) {
                    markSnapshotModified(preview, entry, selection);
                    continue;
                }
            }

            TreeSet<Integer> indexes = new TreeSet<>(entry.chunks.keySet());
            indexes.addAll(current.keySet());
            for (int index : indexes) {
                if (selection != null && !selection.matches(entry, index)) {
                    continue;
                }
                String snapshotHash = entry.chunks.get(index);
                String currentHash = current.containsKey(index)
                        ? RawMca.hashBytes(current.get(index)) : null;

                State state;
                if (snapshotHash == null) {
                    if (selection != null) {
                        // 限区块恢复不会删除快照中不存在的区块
                        continue;
                    }
                    state = State.DELETED;
                } else if (currentHash == null || !snapshotHash.equals(currentHash)) {
                    state = State.MODIFIED;
                } else {
                    state = State.UNCHANGED;
                }

                int chunkX = entry.regionX * 32 + (index & 31);
                int chunkZ = entry.regionZ * 32 + (index >>> 5);
                preview.put(entry.dimension == null ? "" : entry.dimension,
                        new ChunkPos(chunkX, chunkZ), state);
            }
        }
        return preview;
    }

    private static void markSnapshotModified(ChunkPreviewMap preview, Manifest.FileEntry entry,
                                             ChunkSelection selection) {
        for (int index : entry.chunks.keySet()) {
            if (selection != null && !selection.matches(entry, index)) {
                continue;
            }
            int chunkX = entry.regionX * 32 + (index & 31);
            int chunkZ = entry.regionZ * 32 + (index >>> 5);
            preview.put(entry.dimension == null ? "" : entry.dimension,
                    new ChunkPos(chunkX, chunkZ), State.MODIFIED);
        }
    }

    /** 便于 HUD 统计各状态数量。 */
    public Map<State, Integer> counts(String dimension) {
        Map<State, Integer> counts = new EnumMap<>(State.class);
        for (State state : this.forDimension(dimension).values()) {
            counts.merge(state, 1, Integer::sum);
        }
        return counts;
    }
}
