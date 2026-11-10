package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitTest {
    private static final String MANIFEST_ID = "f".repeat(64);

    @Test
    void idIsStableAndIgnoresEditableFields() {
        Commit commit = Commit.create(null, MANIFEST_ID, 1000L, "gsave.snapshot.reason.manual",
                "world", "1.21.8");
        String originalId = commit.id;

        commit.note = "my note";
        commit.pinned = true;
        commit.durationMs = 1234L;

        Commit parsed = Commit.fromJson(commit.toJson());
        assertEquals(originalId, parsed.id);
        assertEquals("my note", parsed.note);
        assertTrue(parsed.pinned);
        assertEquals(1234L, parsed.durationMs);
    }

    @Test
    void samePayloadProducesSameId() {
        Commit first = Commit.create(null, MANIFEST_ID, 1000L, "msg", "world", "1.21.8");
        Commit second = Commit.create(null, MANIFEST_ID, 1000L, "msg", "world", "1.21.8");
        assertEquals(first.id, second.id);
    }

    @Test
    void shortIdUsesFirstEightCharacters() {
        Commit commit = Commit.create(null, MANIFEST_ID, 1000L, "msg", "world", "1.21.8");
        assertEquals(commit.id.substring(0, 8), commit.shortId());
        assertFalse(commit.pinned);
    }
}
