package com.mingdai.savegit.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 仓库维护任务的进度状态，供维护界面轮询显示。
 */
public final class MaintenanceProgress {
    private static final int MAX_LOG_LINES = 300;

    private static volatile boolean active;
    private static volatile boolean finished;
    private static volatile boolean failed;
    private static volatile int percent;
    private static volatile String titleKey = "";
    private static volatile String errorMessage;
    private static final List<String> LOGS = Collections.synchronizedList(new ArrayList<>());

    private MaintenanceProgress() {
    }

    public static void begin(String titleKey) {
        LOGS.clear();
        MaintenanceProgress.titleKey = titleKey;
        percent = 0;
        errorMessage = null;
        failed = false;
        finished = false;
        active = true;
    }

    public static void setPercent(int value) {
        percent = Math.max(0, Math.min(100, value));
    }

    public static void addLog(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        synchronized (LOGS) {
            LOGS.add(line);
            while (LOGS.size() > MAX_LOG_LINES) {
                LOGS.remove(0);
            }
        }
    }

    public static void finish() {
        percent = 100;
        finished = true;
        active = false;
    }

    public static void fail(String message) {
        errorMessage = message == null
                ? com.mingdai.savegit.util.L10n.tr("gsave.progress.error.unknown")
                : message;
        failed = true;
        finished = true;
        active = false;
        addLog(com.mingdai.savegit.util.L10n.tr("gsave.maintenance.log.failed", errorMessage));
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isFinished() {
        return finished;
    }

    public static boolean isFailed() {
        return failed;
    }

    public static int getPercent() {
        return percent;
    }

    public static String getTitleKey() {
        return titleKey;
    }

    public static String getErrorMessage() {
        return errorMessage;
    }

    public static List<String> getLogs() {
        synchronized (LOGS) {
            return new ArrayList<>(LOGS);
        }
    }
}
