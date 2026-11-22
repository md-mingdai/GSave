package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WorldModEnvironmentTest {
    private static final String SNAPSHOT = "{\"mods\":[{\"id\":\"a\",\"version\":\"1.0\"},"
            + "{\"id\":\"b\",\"version\":\"2.0\"},{\"id\":\"c\",\"version\":\"3.0\"}]}";

    @Test
    void missingFromReportsOnlyAbsentMods() {
        WorldModEnvironment snapshot = WorldModEnvironment.fromJson(SNAPSHOT);
        WorldModEnvironment current = WorldModEnvironment.fromJson(
                "{\"mods\":[{\"id\":\"a\",\"version\":\"1.0\"},{\"id\":\"c\",\"version\":\"9.9\"}]}");
        List<String> missing = snapshot.missingFrom(current);
        assertEquals(List.of("b 2.0"), missing);
    }

    @Test
    void missingFromIsEmptyWhenAllPresent() {
        WorldModEnvironment snapshot = WorldModEnvironment.fromJson(SNAPSHOT);
        WorldModEnvironment current = WorldModEnvironment.fromJson(SNAPSHOT);
        assertEquals(List.of(), snapshot.missingFrom(current));
    }

    @Test
    void fingerprintChangesWithVersions() {
        WorldModEnvironment a = WorldModEnvironment.fromJson(SNAPSHOT);
        WorldModEnvironment b = WorldModEnvironment.fromJson(
                "{\"mods\":[{\"id\":\"a\",\"version\":\"1.1\"},"
                        + "{\"id\":\"b\",\"version\":\"2.0\"},{\"id\":\"c\",\"version\":\"3.0\"}]}");
        assertNotEquals(a.fingerprint(), b.fingerprint());
    }

    @Test
    void jsonRoundTripKeepsMods() {
        WorldModEnvironment snapshot = WorldModEnvironment.fromJson(SNAPSHOT);
        WorldModEnvironment again = WorldModEnvironment.fromJson(snapshot.toJson());
        assertEquals(3, again.mods.size());
        assertEquals(snapshot.fingerprint(), again.fingerprint());
    }
}
