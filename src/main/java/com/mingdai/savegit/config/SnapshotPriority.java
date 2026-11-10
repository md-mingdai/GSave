package com.mingdai.savegit.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/**
 * 快照执行优先级。
 */
public enum SnapshotPriority implements IConfigOptionListEntry {
    GAMEPLAY("gameplay", "gsave.config.value.gameplay"),
    SPEED("speed", "gsave.config.value.speed");

    private final String id;
    private final String displayName;

    SnapshotPriority(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String getStringValue() {
        return this.id;
    }

    @Override
    public String getDisplayName() {
        return com.mingdai.savegit.util.L10n.tr(this.displayName);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        int index = this.ordinal();
        if (forward) {
            index = (index + 1) % values().length;
        } else {
            index = (index - 1 + values().length) % values().length;
        }
        return values()[index];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (SnapshotPriority priority : values()) {
            if (priority.id.equalsIgnoreCase(value)) {
                return priority;
            }
        }
        return GAMEPLAY;
    }
}
