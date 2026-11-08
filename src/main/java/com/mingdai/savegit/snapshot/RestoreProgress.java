package com.mingdai.savegit.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 恢复进度的线程安全快照状态，供恢复进度界面轮询显示。
 */
public final class RestoreProgress {
    private static final int MAX_LOG_LINES = 300;

    private static volatile boolean active;
    private static volatile boolean finished;
    private static volatile boolean failed;
    private static volatile int total;
    private static volatile int completed;
    private static volatile String currentFile = "";
    private static volatile String errorMessage;
    private static final List<String> LOGS = Collections.synchronizedList(new ArrayList<>());

    private RestoreProgress() {
    }

    public static void begin(int totalFiles) {
        LOGS.clear();
        total = Math.max(0, totalFiles);
        completed = 0;
        currentFile = "";
        errorMessage = null;
        failed = false;
        finished = false;
        active = true;
        addLog("开始恢复，共 " + total + " 个文件");
    }

    public static void startFile(int index, String path) {
        completed = index;
        currentFile = path == null ? "" : path;
        addLog("正在恢复: " + currentFile);
    }

    public static void completeFile() {
        if (completed < total) {
            completed++;
        }
    }

    public static void finish() {
        completed = total;
        currentFile = "";
        finished = true;
        active = false;
        addLog("恢复完成");
    }

    public static void fail(String message) {
        errorMessage = message == null ? "未知错误" : message;
        failed = true;
        finished = true;
        active = false;
        addLog("恢复失败: " + errorMessage);
    }

    public static void addLog(String line) {
        synchronized (LOGS) {
            LOGS.add(line);
            while (LOGS.size() > MAX_LOG_LINES) {
                LOGS.remove(0);
            }
        }
    }

    public static List<String> getLogs() {
        synchronized (LOGS) {
            return new ArrayList<>(LOGS);
        }
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

    public static int getTotal() {
        return total;
    }

    public static int getCompleted() {
        return completed;
    }

    public static String getCurrentFile() {
        return currentFile;
    }

    public static String getErrorMessage() {
        return errorMessage;
    }

    public static int getPercent() {
        if (finished && !failed) {
            return 100;
        }
        if (total <= 0) {
            return finished ? 100 : 0;
        }
        return Math.max(0, Math.min(100, completed * 100 / total));
    }
}
