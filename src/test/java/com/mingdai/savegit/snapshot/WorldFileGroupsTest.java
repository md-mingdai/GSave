package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFileGroupsTest {
    @Test
    void excludesGaveAndTransientFiles() {
        assertTrue(WorldFileGroups.isExcluded("gsave/data"));
        assertTrue(WorldFileGroups.isExcluded("gsave"));
        assertTrue(WorldFileGroups.isExcluded("session.lock"));
        assertTrue(WorldFileGroups.isExcluded("level.dat_old"));
        assertTrue(WorldFileGroups.isExcluded("foo.tmp"));
        assertTrue(WorldFileGroups.isExcluded("foo.lock"));
        assertTrue(WorldFileGroups.isExcluded(".DS_Store"));
        assertFalse(WorldFileGroups.isExcluded("datapacks/pack.zip"));
        assertFalse(WorldFileGroups.isExcluded("modsdata/quests.dat"));
        assertTrue(WorldFileGroups.isExcluded("level.dat"));
        assertTrue(WorldFileGroups.isExcluded("playerdata/abc.dat"));
    }

    @Test
    void classifiesDatapacksAndOtherFiles() {
        assertTrue(WorldFileGroups.isDatapack("datapacks/pack.zip"));
        assertEquals(WorldFileGroups.GROUP_DATAPACKS, WorldFileGroups.classify("datapacks/pack.zip"));
        assertEquals(WorldFileGroups.GROUP_OTHER, WorldFileGroups.classify("modsdata/quests.dat"));
        assertFalse(WorldFileGroups.isDatapack("datapacksx/pack.zip"));
    }
}
