package com.mingdai.savegit.preview;

import com.mingdai.savegit.snapshot.ChunkPreviewMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把区块状态网格合并成同状态的矩形区域,减少预览时要绘制的方框数量。
 */
public final class PreviewRectangles {
    public record Rect(int minX, int minZ, int maxX, int maxZ, ChunkPreviewMap.State state) {
        public int centerX() {
            return this.minX + this.maxX;
        }

        public int centerZ() {
            return this.minZ + this.maxZ;
        }
    }

    private PreviewRectangles() {
    }

    /** 网格坐标为区块坐标,直接使用 map 的键。 */
    public static List<Rect> merge(Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> cells) {
        List<Rect> rects = new ArrayList<>();
        if (cells == null || cells.isEmpty()) {
            return rects;
        }

        Map<Long, ChunkPreviewMap.State> grid = new HashMap<>(cells.size() * 2);
        for (Map.Entry<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> entry : cells.entrySet()) {
            ChunkPreviewMap.ChunkPos pos = entry.getKey();
            grid.put(key(pos.x(), pos.z()), entry.getValue());
        }

        Set<Long> used = new HashSet<>(cells.size() * 2);
        List<Long> keys = new ArrayList<>(grid.keySet());
        keys.sort((a, b) -> {
            long ax = longX(a);
            long az = longZ(a);
            long bx = longX(b);
            long bz = longZ(b);
            int cmp = Long.compare(az, bz);
            return cmp != 0 ? cmp : Long.compare(ax, bx);
        });

        for (long key : keys) {
            if (used.contains(key)) {
                continue;
            }
            long x = longX(key);
            long z = longZ(key);
            ChunkPreviewMap.State state = grid.get(key);

            long maxX = x;
            while (true) {
                Long next = key2(maxX + 1, z);
                if (next == null || used.contains(next) || grid.get(next) != state) {
                    break;
                }
                maxX++;
            }

            long maxZ = z;
            while (true) {
                long nextZ = maxZ + 1;
                boolean rowMatches = true;
                for (long cx = x; cx <= maxX; cx++) {
                    Long next = key2(cx, nextZ);
                    if (next == null || used.contains(next) || grid.get(next) != state) {
                        rowMatches = false;
                        break;
                    }
                }
                if (!rowMatches) {
                    break;
                }
                maxZ = nextZ;
            }

            for (long cx = x; cx <= maxX; cx++) {
                for (long cz = z; cz <= maxZ; cz++) {
                    Long cell = key2(cx, cz);
                    if (cell != null) {
                        used.add(cell);
                    }
                }
            }

            rects.add(new Rect((int) x, (int) z, (int) maxX, (int) maxZ, state));
        }
        return rects;
    }

    private static Long key2(long x, long z) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            return null;
        }
        return key((int) x, (int) z);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static long longX(long key) {
        return key >> 32;
    }

    private static long longZ(long key) {
        return (int) key;
    }
}
