package com.mingdai.savegit.snapshot;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 一组待恢复的区块（世界区块坐标），由请求时刻玩家所在位置生成。
 * 半径以 16 方块（1 个区块）为单位：r = 1 只包含玩家所在区块，
 * r = 2 覆盖玩家所在区块周围 3×3 个区块（约 32 方块半径），依此类推。
 */
public final class ChunkSelection {
    public record ChunkPos(int x, int z) {
    }

    private final String dimension;
    private final Set<ChunkPos> chunks;

    public ChunkSelection(String dimension, Set<ChunkPos> chunks) {
        this.dimension = dimension;
        this.chunks = Collections.unmodifiableSet(new HashSet<>(chunks));
    }

    public static ChunkSelection around(String dimension, int centerX, int centerZ, int radiusChunks) {
        int radius = Math.max(1, radiusChunks);
        Set<ChunkPos> chunks = new HashSet<>();
        for (int dx = -(radius - 1); dx <= radius - 1; dx++) {
            for (int dz = -(radius - 1); dz <= radius - 1; dz++) {
                chunks.add(new ChunkPos(centerX + dx, centerZ + dz));
            }
        }
        return new ChunkSelection(dimension, chunks);
    }

    public String dimension() {
        return this.dimension;
    }

    public Set<ChunkPos> chunks() {
        return this.chunks;
    }

    public int size() {
        return this.chunks.size();
    }

    /** 判断 manifest 中某个文件条目的区块 index 是否属于本次选择。 */
    public boolean matches(Manifest.FileEntry entry, int index) {
        if (entry == null || entry.dimension == null || !entry.dimension.equals(this.dimension)) {
            return false;
        }
        int chunkX = entry.regionX * 32 + (index & 31);
        int chunkZ = entry.regionZ * 32 + (index >>> 5);
        return this.chunks.contains(new ChunkPos(chunkX, chunkZ));
    }

    /** 该文件条目是否至少包含一个被选中的区块。 */
    public boolean intersects(Manifest.FileEntry entry) {
        if (entry == null || !entry.isMca() || entry.chunks.isEmpty()) {
            return false;
        }
        for (int index : entry.chunks.keySet()) {
            if (this.matches(entry, index)) {
                return true;
            }
        }
        return false;
    }
}
