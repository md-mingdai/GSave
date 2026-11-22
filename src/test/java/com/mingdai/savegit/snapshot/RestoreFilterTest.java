package com.mingdai.savegit.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreFilterTest {
    private static Manifest.FileEntry whole(String path) {
        Manifest.FileEntry entry = new Manifest.FileEntry();
        entry.type = "whole";
        entry.path = path;
        return entry;
    }

    @Test
    void datapacksUseTheirOwnToggle() {
        Manifest.FileEntry datapack = whole("datapacks/pack.zip");
        Manifest.FileEntry other = whole("modsdata/quests.dat");

        WorldRestorer.RestoreFilter withoutDatapacks =
                new WorldRestorer.RestoreFilter(false, false, false, true, false);
        assertFalse(withoutDatapacks.accepts(datapack));
        assertTrue(withoutDatapacks.accepts(other));

        WorldRestorer.RestoreFilter onlyDatapacks =
                new WorldRestorer.RestoreFilter(false, false, false, false, true);
        assertTrue(onlyDatapacks.accepts(datapack));
        assertFalse(onlyDatapacks.accepts(other));
    }
}
