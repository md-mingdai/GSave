package com.mingdai.savegit.mca;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawMcaTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsChunkRecordsByteForByte() throws IOException {
        byte[] first = record(2, new byte[]{1, 2, 3, 4, 5});
        byte[] second = record(3, new byte[]{9, 8, 7});

        Map<Integer, byte[]> records = new TreeMap<>();
        records.put(0, first);
        records.put(7, second);

        Path file = tempDir.resolve("r.0.0.mca");
        RawMca.write(file, records);

        Map<Integer, byte[]> readBack = RawMca.readChunks(file);
        assertEquals(2, readBack.size());
        assertArrayEquals(first, readBack.get(0));
        assertArrayEquals(second, readBack.get(7));
    }

    @Test
    void detectsExternalChunkSentinel() throws IOException {
        Path file = tempDir.resolve("r.1.1.mca");
        byte[] data = new byte[RawMca.HEADER_BYTES];
        int entry = (2 << 8) | 255;
        data[0] = (byte) (entry >>> 24);
        data[1] = (byte) (entry >>> 16);
        data[2] = (byte) (entry >>> 8);
        data[3] = (byte) entry;
        Files.write(file, data);

        assertTrue(RawMca.hasExternalChunks(file));
        assertEquals(0, RawMca.readChunks(file).size());
    }

    @Test
    void headerShorterThanAnvilYieldsNoLocations() {
        RawMca.Location[] locations = RawMca.readLocations(new byte[16]);
        assertEquals(RawMca.LOCATION_COUNT, locations.length);
        assertFalse(locations[0].isPresent());
    }

    @Test
    void hashIsStable() {
        byte[] bytes = {1, 2, 3};
        assertEquals(RawMca.hashBytes(bytes), RawMca.hashBytes(bytes));
        assertEquals(64, RawMca.hashBytes(bytes).length());
    }

    private static byte[] record(int compression, byte[] payload) {
        byte[] record = new byte[5 + payload.length];
        int length = payload.length + 1;
        record[0] = (byte) (length >>> 24);
        record[1] = (byte) (length >>> 16);
        record[2] = (byte) (length >>> 8);
        record[3] = (byte) length;
        record[4] = (byte) compression;
        System.arraycopy(payload, 0, record, 5, payload.length);
        return record;
    }
}
