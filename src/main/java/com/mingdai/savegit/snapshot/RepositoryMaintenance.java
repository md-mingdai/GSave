package com.mingdai.savegit.snapshot;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.util.L10n;

import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * 在后台线程执行仓库自检/修复/清理，并与快照创建共用同一把锁。
 */
public final class RepositoryMaintenance {
    private static volatile RepositoryChecker.Report lastReport;
    private static volatile RepositoryGc.Result lastGcResult;
    private static volatile int lastRepairCount = -1;

    private RepositoryMaintenance() {
    }

    @FunctionalInterface
    private interface Task {
        void run(BiConsumer<Integer, String> progress) throws Exception;
    }

    public static boolean startCheck(Path worldRoot) {
        return start("gsave.maintenance.title.check", progress -> {
            RepositoryChecker.Report report = RepositoryChecker.check(worldRoot, progress);
            lastReport = report;
            int errors = (int) report.count(RepositoryChecker.Level.ERROR);
            int warnings = (int) report.count(RepositoryChecker.Level.WARNING);
            int infos = (int) report.count(RepositoryChecker.Level.INFO);
            progress.accept(100, L10n.tr("gsave.check.log.summary",
                    String.valueOf(errors), String.valueOf(warnings), String.valueOf(infos)));
        });
    }

    public static boolean startRepair(Path worldRoot) {
        return start("gsave.maintenance.title.repair", progress -> {
            progress.accept(10, L10n.tr("gsave.repair.log.start"));
            int repaired = RepositoryChecker.repair(worldRoot);
            lastRepairCount = repaired;
            progress.accept(100, L10n.tr("gsave.repair.log.done",
                    String.valueOf(repaired)));
        });
    }

    public static boolean startGc(Path worldRoot) {
        return start("gsave.maintenance.title.gc", progress -> {
            RepositoryGc.Result result = RepositoryGc.collect(worldRoot, progress);
            lastGcResult = result;
            progress.accept(100, L10n.tr("gsave.gc.log.summary",
                    String.valueOf(result.removedBlobs()),
                    formatBytes(result.freedBytes()),
                    String.valueOf(result.orphanFiles())));
        });
    }

    private static boolean start(String titleKey, Task task) {
        if (MaintenanceProgress.isActive()) {
            return false;
        }
        MaintenanceProgress.begin(titleKey);
        Thread thread = new Thread(() -> {
            try {
                WorldSnapshotter.runLocked(() -> task.run(
                        (percent, message) -> {
                            MaintenanceProgress.setPercent(percent);
                            MaintenanceProgress.addLog(message);
                        }));
                MaintenanceProgress.finish();
            } catch (Throwable t) {
                SaveGitMod.LOGGER.error("仓库维护任务失败", t);
                MaintenanceProgress.fail(t.getMessage());
            }
        }, "GSave-Maintenance");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public static RepositoryChecker.Report getLastReport() {
        return lastReport;
    }

    public static RepositoryGc.Result getLastGcResult() {
        return lastGcResult;
    }

    public static int getLastRepairCount() {
        return lastRepairCount;
    }

    public static void clearLastResults() {
        lastReport = null;
        lastGcResult = null;
        lastRepairCount = -1;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        for (String unit : units) {
            value /= 1024.0;
            if (value < 1024.0) {
                return String.format(java.util.Locale.ROOT, "%.1f %s", value, unit);
            }
        }
        return String.format(java.util.Locale.ROOT, "%.1f PiB", value / 1024.0);
    }
}
