package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestTest {
    @Test
    void jsonRoundTripPreservesEntriesAndHash() {
        Manifest manifest = new Manifest();
        Map<Integer, String> chunks = new TreeMap<>();
        chunks.put(1, "a".repeat(64));
        chunks.put(2, "b".repeat(64));
        manifest.addMcaFile("region/r.0.0.mca", "minecraft:overworld", "region", 0, 0, chunks);
        manifest.addWholeFile("level.dat", null, "c".repeat(64));

        String json = manifest.toJson();
        Manifest parsed = Manifest.fromJson(json);
        assertEquals(manifest.computeHash(), parsed.computeHash());
        assertNotNull(parsed.getEntry("region/r.0.0.mca"));
        assertNotNull(parsed.getEntry("level.dat"));
        assertEquals(2, parsed.getEntry("region/r.0.0.mca").chunks.size());
        assertTrue(parsed.getEntry("region/r.0.0.mca").isMca());
        assertTrue(parsed.getEntry("level.dat").isWhole());
    }

    @Test
    void duplicateAddsCollapseIntoOneEntryPerPath() {
        Manifest manifest = new Manifest();
        manifest.addWholeFile("level.dat", null, "d".repeat(64));
        manifest.addWholeFile("level.dat", null, "e".repeat(64));
        assertEquals(1, manifest.files.size());
        assertEquals("e".repeat(64), manifest.getEntry("level.dat").hash);
    }

    @Test
    void fromBrokenJsonReturnsEmptyManifest() {
        Manifest manifest = Manifest.fromJson("{\"files\":42}");
        assertTrue(manifest.files.isEmpty());
        assertNull(manifest.getEntry("missing"));
    }
}
