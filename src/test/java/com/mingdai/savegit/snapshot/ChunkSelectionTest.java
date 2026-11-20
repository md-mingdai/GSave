package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSelectionTest {
    private static Manifest.FileEntry entry(String dimension, int regionX, int regionZ, int... indexes) {
        Manifest.FileEntry entry = new Manifest.FileEntry();
        entry.type = "mca";
        entry.path = "region/r." + regionX + "." + regionZ + ".mca";
        entry.dimension = dimension;
        entry.regionX = regionX;
        entry.regionZ = regionZ;
        for (int index : indexes) {
            entry.chunks.put(index, "hash-" + index);
        }
        return entry;
    }

    @Test
    void radiusOneSelectsOnlyCenterChunk() {
        ChunkSelection selection = ChunkSelection.around("minecraft:overworld", 10, -5, 1);
        assertEquals(1, selection.size());
        assertTrue(selection.chunks().contains(new ChunkSelection.ChunkPos(10, -5)));
    }

    @Test
    void radiusTwoSelectsThreeByThree() {
        ChunkSelection selection = ChunkSelection.around("minecraft:overworld", 0, 0, 2);
        assertEquals(9, selection.size());
        assertTrue(selection.chunks().contains(new ChunkSelection.ChunkPos(-1, -1)));
        assertTrue(selection.chunks().contains(new ChunkSelection.ChunkPos(1, 1)));
        assertFalse(selection.chunks().contains(new ChunkSelection.ChunkPos(2, 0)));
    }

    @Test
    void matchesSelectsChunkInsideRegion() {
        // chunk (10, -5) -> region (0, -1), index = (10 & 31) + ((-5 & 31) << 5) = 874
        ChunkSelection selection = ChunkSelection.around("minecraft:overworld", 10, -5, 2);
        Manifest.FileEntry entry = entry("minecraft:overworld", 0, -1, 874, 875);
        assertTrue(selection.matches(entry, 874));
        assertTrue(selection.matches(entry, 875));
        assertTrue(selection.intersects(entry));

        ChunkSelection centerOnly = ChunkSelection.around("minecraft:overworld", 10, -5, 1);
        assertTrue(centerOnly.matches(entry, 874));
        assertFalse(centerOnly.matches(entry, 875));
    }

    @Test
    void matchesRejectsOtherDimension() {
        ChunkSelection selection = ChunkSelection.around("minecraft:overworld", 10, -5, 1);
        Manifest.FileEntry entry = entry("minecraft:the_nether", 0, -1, 874);
        assertFalse(selection.intersects(entry));
    }

    @Test
    void negativeChunkCoordinatesUseCorrectRegion() {
        // chunk (-1, -1) -> region (-1, -1), index = 31 + 31 * 32 = 1023
        ChunkSelection selection = ChunkSelection.around("minecraft:overworld", -1, -1, 1);
        Manifest.FileEntry entry = entry("minecraft:overworld", -1, -1, 1023);
        assertTrue(selection.matches(entry, 1023));
    }
}
