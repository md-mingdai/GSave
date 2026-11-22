package com.mingdai.savegit.preview;

import com.mingdai.savegit.snapshot.ChunkPreviewMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewRectanglesTest {
    private static ChunkPreviewMap.ChunkPos pos(int x, int z) {
        return new ChunkPreviewMap.ChunkPos(x, z);
    }

    @Test
    void mergesSingleRunIntoOneRect() {
        Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> cells = new HashMap<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 2; z++) {
                cells.put(pos(x, z), ChunkPreviewMap.State.MODIFIED);
            }
        }

        List<PreviewRectangles.Rect> rects = PreviewRectangles.merge(cells);
        assertEquals(1, rects.size());
        PreviewRectangles.Rect rect = rects.get(0);
        assertEquals(0, rect.minX());
        assertEquals(0, rect.minZ());
        assertEquals(2, rect.maxX());
        assertEquals(1, rect.maxZ());
        assertEquals(ChunkPreviewMap.State.MODIFIED, rect.state());
    }

    @Test
    void keepsDifferentStatesSeparate() {
        Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> cells = new HashMap<>();
        cells.put(pos(0, 0), ChunkPreviewMap.State.MODIFIED);
        cells.put(pos(1, 0), ChunkPreviewMap.State.DELETED);
        cells.put(pos(0, 1), ChunkPreviewMap.State.UNCHANGED);

        List<PreviewRectangles.Rect> rects = PreviewRectangles.merge(cells);
        assertEquals(3, rects.size());
    }

    @Test
    void handlesNegativeCoordinates() {
        Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> cells = new HashMap<>();
        cells.put(pos(-2, -3), ChunkPreviewMap.State.DELETED);
        cells.put(pos(-1, -3), ChunkPreviewMap.State.DELETED);

        List<PreviewRectangles.Rect> rects = PreviewRectangles.merge(cells);
        assertEquals(1, rects.size());
        assertEquals(-2, rects.get(0).minX());
        assertEquals(-1, rects.get(0).maxX());
        assertEquals(-3, rects.get(0).minZ());
    }

    @Test
    void emptyMapYieldsNothing() {
        assertTrue(PreviewRectangles.merge(Map.of()).isEmpty());
    }
}
