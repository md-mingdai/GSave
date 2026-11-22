package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.mca.RawMca;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPreviewMapTest {
    @TempDir
    Path tempDir;

    private static byte[] record(int payload) {
        return new byte[]{0, 0, 0, 1, (byte) payload};
    }

    private Manifest manifestWithChunk(int index, byte[] record) {
        Manifest manifest = new Manifest();
        Map<Integer, String> chunks = new TreeMap<>();
        chunks.put(index, RawMca.hashBytes(record));
        manifest.addMcaFile("region/r.0.0.mca", "minecraft:overworld", "region", 0, 0, chunks);
        return manifest;
    }

    private void writeCurrent(String relative, Map<Integer, byte[]> records) throws Exception {
        Path file = this.tempDir.resolve(relative);
        RawMca.write(file, records);
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void reportsUnchangedAndDeletedChunks() throws Exception {
        Map<Integer, byte[]> current = new TreeMap<>();
        current.put(0, record(1));
        current.put(1, record(2));
        this.writeCurrent("region/r.0.0.mca", current);

        ChunkPreviewMap preview = ChunkPreviewMap.compute(this.tempDir,
                this.manifestWithChunk(0, record(1)), WorldRestorer.RestoreFilter.ALL, null);

        assertEquals(ChunkPreviewMap.State.UNCHANGED,
                preview.forDimension("minecraft:overworld").get(new ChunkPreviewMap.ChunkPos(0, 0)));
        assertEquals(ChunkPreviewMap.State.DELETED,
                preview.forDimension("minecraft:overworld").get(new ChunkPreviewMap.ChunkPos(1, 0)));
    }

    @Test
    void reportsModifiedChunk() throws Exception {
        Map<Integer, byte[]> current = new TreeMap<>();
        current.put(0, record(9));
        this.writeCurrent("region/r.0.0.mca", current);

        ChunkPreviewMap preview = ChunkPreviewMap.compute(this.tempDir,
                this.manifestWithChunk(0, record(1)), WorldRestorer.RestoreFilter.ALL, null);

        assertEquals(ChunkPreviewMap.State.MODIFIED,
                preview.forDimension("minecraft:overworld").get(new ChunkPreviewMap.ChunkPos(0, 0)));
    }

    @Test
    void chunkSelectionOnlyMarksSelectedChunksAndNeverDeletes() throws Exception {
        Map<Integer, byte[]> current = new TreeMap<>();
        current.put(0, record(1));
        current.put(1, record(2));
        this.writeCurrent("region/r.0.0.mca", current);

        ChunkSelection selection = ChunkSelection.around("minecraft:overworld", 0, 0, 1);
        ChunkPreviewMap preview = ChunkPreviewMap.compute(this.tempDir,
                this.manifestWithChunk(0, record(1)), WorldRestorer.RestoreFilter.ALL, selection);

        assertEquals(1, preview.totalChunks());
        assertEquals(ChunkPreviewMap.State.UNCHANGED,
                preview.forDimension("minecraft:overworld").get(new ChunkPreviewMap.ChunkPos(0, 0)));
        assertNull(preview.forDimension("minecraft:overworld").get(new ChunkPreviewMap.ChunkPos(1, 0)));
    }

    @Test
    void mapsRegionIndexesToWorldChunkCoordinates() throws Exception {
        // region (1, -1), chunk (33, -1) -> index = 1 + 31*32 = 993
        Manifest manifest = new Manifest();
        Map<Integer, String> chunks = new TreeMap<>();
        chunks.put(993, RawMca.hashBytes(record(1)));
        manifest.addMcaFile("region/r.1.-1.mca", "minecraft:overworld", "region", 1, -1, chunks);

        ChunkPreviewMap preview = ChunkPreviewMap.compute(this.tempDir, manifest,
                WorldRestorer.RestoreFilter.ALL, null);

        assertEquals(ChunkPreviewMap.State.MODIFIED,
                preview.forDimension("minecraft:overworld").get(new ChunkPreviewMap.ChunkPos(33, -1)));
    }
}
