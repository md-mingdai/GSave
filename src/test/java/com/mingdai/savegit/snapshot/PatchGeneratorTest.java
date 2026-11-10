package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatchGeneratorTest {
    @Test
    void detectsAddedModifiedAndRemovedChunks() {
        Manifest oldManifest = new Manifest();
        Map<Integer, String> oldChunks = new TreeMap<>();
        oldChunks.put(0, "a".repeat(64));
        oldChunks.put(1, "b".repeat(64));
        oldManifest.addMcaFile("region/r.0.0.mca", "minecraft:overworld", "region", 0, 0, oldChunks);

        Manifest newManifest = new Manifest();
        Map<Integer, String> newChunks = new TreeMap<>();
        newChunks.put(0, "c".repeat(64));
        newChunks.put(1, "b".repeat(64));
        newChunks.put(2, "d".repeat(64));
        newManifest.addMcaFile("region/r.0.0.mca", "minecraft:overworld", "region", 0, 0, newChunks);

        Patch patch = PatchGenerator.generate("commit", "parent", oldManifest, newManifest);
        Patch.FileChange change = patch.files.get("region/r.0.0.mca");
        assertEquals(Patch.ChangeType.MODIFIED, change.change);

        int modified = 0;
        int added = 0;
        int removed = 0;
        for (Patch.ChunkChange chunk : change.chunks.values()) {
            switch (chunk.type) {
                case MODIFIED -> modified++;
                case ADDED -> added++;
                case REMOVED -> removed++;
            }
        }
        assertEquals(1, modified);
        assertEquals(1, added);
        assertEquals(0, removed);
    }

    @Test
    void wholeFileChangesAreReported() {
        Manifest oldManifest = new Manifest();
        oldManifest.addWholeFile("level.dat", null, "a".repeat(64));
        Manifest newManifest = new Manifest();
        newManifest.addWholeFile("level.dat", null, "b".repeat(64));

        Patch patch = PatchGenerator.generate("c", "p", oldManifest, newManifest);
        Patch.FileChange change = patch.files.get("level.dat");
        assertEquals(Patch.ChangeType.MODIFIED, change.change);
        assertEquals("a".repeat(64), change.oldHash);
        assertEquals("b".repeat(64), change.newHash);
    }

    @Test
    void identicalManifestsProduceEmptyPatch() {
        Manifest manifest = new Manifest();
        manifest.addWholeFile("level.dat", null, "a".repeat(64));
        Patch patch = PatchGenerator.generate("c", "p", manifest, manifest);
        assertEquals(0, patch.files.size());
        assertNull(patch.files.get("level.dat"));
    }
}
