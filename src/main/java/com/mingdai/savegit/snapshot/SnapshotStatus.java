package com.mingdai.savegit.snapshot;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局快照进行中状态。任何触发路径开始/结束时配对调用，HUD 据此持续显示提示。
 */
public final class SnapshotStatus {
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static volatile String label = "";

    private SnapshotStatus() {
    }

    public static void begin(String label) {
        ACTIVE.incrementAndGet();
        SnapshotStatus.label = label == null ? "" : label;
    }

    public static void end() {
        ACTIVE.updateAndGet(value -> Math.max(0, value - 1));
    }

    public static boolean isActive() {
        return ACTIVE.get() > 0;
    }

    public static String getLabel() {
        return label;
    }

    public static void reset() {
        ACTIVE.set(0);
        label = "";
    }
}
