package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.MaintenanceProgress;
import com.mingdai.savegit.snapshot.RepositoryChecker;
import com.mingdai.savegit.snapshot.RepositoryMaintenance;
import com.mingdai.savegit.snapshot.RepositoryStats;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.util.SnapshotTime;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.nio.file.Path;
import java.util.List;

/**
 * 离线仓库维护：占用统计、自检结果、修复与清理未引用数据。
 */
public class GuiRepositoryMaintenance extends GuiSaveGitScreen {
    private final Path worldDir;
    private final String worldDisplayName;
    private final Screen returnScreen;

    private RepositoryStats.Stats stats;
    private boolean statsLoading;
    private boolean sawActiveTask;

    public GuiRepositoryMaintenance(Path worldDir, String worldDisplayName, Screen returnScreen) {
        this.worldDir = worldDir;
        this.worldDisplayName = worldDisplayName;
        this.returnScreen = returnScreen;
        this.setTitle(L10n.tr("gsave.maintenance.title", worldDisplayName));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.refreshStats();

        int y = this.height - 28;
        int x = 20;
        x = this.addButton(x, y, 60, L10n.tr("gsave.maintenance.button.check"),
                () -> RepositoryMaintenance.startCheck(this.worldDir));
        x = this.addButton(x, y, 60, L10n.tr("gsave.maintenance.button.repair"),
                () -> RepositoryMaintenance.startRepair(this.worldDir));
        x = this.addButton(x, y, 110, L10n.tr("gsave.maintenance.button.gc"),
                () -> GuiBase.openGui(new GuiConfirmMaintenance(this.worldDir,
                        this.worldDisplayName, this)));
        this.addButton(x, y, 60, L10n.tr("gsave.button.refresh"), this::refreshStats);

        int backWidth = 100;
        this.addButton(this.width - 20 - backWidth, y, backWidth,
                L10n.tr("gsave.button.back"), this::goBack);
    }

    private int addButton(int x, int y, int width, String label, Runnable action) {
        this.addButton(new ButtonGeneric(x, y, width, 20, label),
                (button, mouseButton) -> action.run());
        return x + width + 8;
    }

    private void goBack() {
        if (this.returnScreen != null) {
            GuiBase.openGui(this.returnScreen);
        } else {
            this.close();
        }
    }

    private void refreshStats() {
        if (this.statsLoading) {
            return;
        }
        this.statsLoading = true;
        Thread thread = new Thread(() -> {
            RepositoryStats.Stats computed = null;
            try {
                computed = RepositoryStats.compute(this.worldDir);
            } catch (Throwable ignored) {
            }
            RepositoryStats.Stats result = computed;
            this.mc.execute(() -> {
                this.stats = result;
                this.statsLoading = false;
            });
        }, "GSave-Stats");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        boolean active = MaintenanceProgress.isActive();
        boolean finished = MaintenanceProgress.isFinished();
        if (this.sawActiveTask && !active && finished) {
            this.sawActiveTask = false;
            this.refreshStats();
        }
        if (active) {
            this.sawActiveTask = true;
        }

        int left = 20;
        int right = this.width - 20;
        int top = 40;
        int bottom = this.height - 40;
        GuiTheme.drawPanel(drawContext, left, top, right, bottom);

        int x = left + 10;
        int y = top + 10;

        if (this.statsLoading && this.stats == null) {
            this.drawString(drawContext, L10n.tr("gsave.maintenance.label.stats_loading"),
                    x, y, GuiTheme.TEXT_DIM);
        } else if (this.stats != null) {
            y = this.drawStats(drawContext, x, y);
        }

        y += 6;
        GuiTheme.drawDivider(drawContext, x, y, right - 10, 0x40FFFFFF);
        y += 8;

        y = this.drawResults(drawContext, x, y, right - 10);

        GuiTheme.drawDivider(drawContext, x, y, right - 10, 0x40FFFFFF);
        y += 8;
        this.drawProgress(drawContext, x, y, right - 10);
    }

    private int drawStats(DrawContext drawContext, int x, int y) {
        RepositoryStats.Stats value = this.stats;

        String format = L10n.tr("gsave.maintenance.label.format",
                String.valueOf(value.formatVersion()));
        this.drawString(drawContext, format, x, y,
                value.readOnly() ? GuiTheme.WARNING : GuiTheme.TEXT_MAIN);
        if (value.readOnly()) {
            y += 12;
            this.drawString(drawContext, L10n.tr("gsave.maintenance.label.readonly"),
                    x, y, GuiTheme.WARNING);
        }
        y += 12;

        this.drawString(drawContext, L10n.tr("gsave.maintenance.label.snapshots",
                String.valueOf(value.snapshotCount())), x, y, GuiTheme.TEXT_MAIN);
        y += 12;

        if (value.snapshotCount() > 0) {
            String range = L10n.tr("gsave.maintenance.label.range",
                    SnapshotTime.absolute(value.oldestTimestamp()),
                    SnapshotTime.absolute(value.newestTimestamp()));
            this.drawString(drawContext, range, x, y, GuiTheme.TEXT_DIM);
            y += 12;
        }

        this.drawString(drawContext,
                L10n.tr("gsave.maintenance.label.packs",
                        RepositoryMaintenance.formatBytes(value.packBytes())),
                x, y, GuiTheme.TEXT_MAIN);
        y += 12;

        this.drawString(drawContext,
                L10n.tr("gsave.maintenance.label.meta",
                        RepositoryMaintenance.formatBytes(value.commitBytes()),
                        RepositoryMaintenance.formatBytes(value.manifestBytes()),
                        RepositoryMaintenance.formatBytes(value.patchBytes())),
                x, y, GuiTheme.TEXT_DIM);
        y += 12;

        this.drawString(drawContext,
                L10n.tr("gsave.maintenance.label.shots",
                        RepositoryMaintenance.formatBytes(value.shotBytes())),
                x, y, GuiTheme.TEXT_DIM);
        y += 12;

        this.drawString(drawContext,
                L10n.tr("gsave.maintenance.label.blobs",
                        RepositoryMaintenance.formatBytes(value.referencedBlobBytes()),
                        RepositoryMaintenance.formatBytes(value.unreferencedBlobBytes())),
                x, y,
                value.unreferencedBlobBytes() > 0 ? GuiTheme.WARNING : GuiTheme.TEXT_DIM);
        y += 12;

        this.drawString(drawContext,
                L10n.tr("gsave.maintenance.label.total",
                        RepositoryMaintenance.formatBytes(value.totalBytes())),
                x, y, GuiTheme.TEXT_MAIN);
        return y + 12;
    }

    private int drawResults(DrawContext drawContext, int x, int y, int maxX) {
        RepositoryChecker.Report report = RepositoryMaintenance.getLastReport();
        if (report != null) {
            this.drawString(drawContext, L10n.tr("gsave.maintenance.label.last_check",
                            String.valueOf(report.count(RepositoryChecker.Level.ERROR)),
                            String.valueOf(report.count(RepositoryChecker.Level.WARNING)),
                            String.valueOf(report.count(RepositoryChecker.Level.INFO))),
                    x, y, GuiTheme.TEXT_MAIN);
            y += 12;

            int shown = 0;
            for (RepositoryChecker.Issue issue : report.issues()) {
                if (shown >= 8) {
                    break;
                }
                int color = switch (issue.level()) {
                    case ERROR -> GuiTheme.DANGER;
                    case WARNING -> GuiTheme.WARNING;
                    case INFO -> GuiTheme.TEXT_DIM;
                };
                String line = this.textRenderer.trimToWidth(
                        L10n.tr(issue.key(), issue.args()), maxX - x);
                this.drawString(drawContext, line, x + 6, y, color);
                y += 11;
                shown++;
            }
            if (report.issues().size() > shown) {
                this.drawString(drawContext,
                        L10n.tr("gsave.maintenance.label.more_issues",
                                String.valueOf(report.issues().size() - shown)),
                        x + 6, y, GuiTheme.TEXT_FAINT);
                y += 11;
            }
            return y + 4;
        }

        if (RepositoryMaintenance.getLastRepairCount() >= 0) {
            this.drawString(drawContext, L10n.tr("gsave.maintenance.label.last_repair",
                            String.valueOf(RepositoryMaintenance.getLastRepairCount())),
                    x, y, GuiTheme.TEXT_MAIN);
            return y + 12;
        }

        if (RepositoryMaintenance.getLastGcResult() != null) {
            this.drawString(drawContext, L10n.tr("gsave.maintenance.label.last_gc",
                            RepositoryMaintenance.formatBytes(
                                    RepositoryMaintenance.getLastGcResult().freedBytes()),
                            String.valueOf(
                                    RepositoryMaintenance.getLastGcResult().orphanFiles())),
                    x, y, GuiTheme.TEXT_MAIN);
            return y + 12;
        }

        this.drawString(drawContext, L10n.tr("gsave.maintenance.label.no_result"),
                x, y, GuiTheme.TEXT_FAINT);
        return y + 12;
    }

    private void drawProgress(DrawContext drawContext, int x, int y, int maxX) {
        if (!MaintenanceProgress.isActive() && !MaintenanceProgress.isFinished()) {
            this.drawString(drawContext, L10n.tr("gsave.maintenance.label.idle"),
                    x, y, GuiTheme.TEXT_FAINT);
            return;
        }

        String title = MaintenanceProgress.isFinished()
                ? L10n.tr(MaintenanceProgress.isFailed()
                ? "gsave.progress.title.failed" : "gsave.progress.title.finished")
                : L10n.tr(MaintenanceProgress.getTitleKey());
        int titleColor = MaintenanceProgress.isFailed() ? GuiTheme.DANGER : GuiTheme.TEXT_MAIN;
        this.drawString(drawContext, title, x, y, titleColor);
        y += 12;

        int barWidth = maxX - x;
        int barHeight = 8;
        int percent = MaintenanceProgress.getPercent();
        drawContext.fill(x, y, x + barWidth, y + barHeight, 0xFF1A1E23);
        drawContext.fill(x, y, x + barWidth * percent / 100, y + barHeight,
                MaintenanceProgress.isFailed() ? GuiTheme.DANGER : GuiTheme.ACCENT);
        y += barHeight + 4;

        this.drawString(drawContext, percent + "%", x, y, GuiTheme.TEXT_DIM);
        y += 12;

        List<String> logs = MaintenanceProgress.getLogs();
        int start = Math.max(0, logs.size() - 5);
        for (int i = start; i < logs.size(); i++) {
            String line = this.textRenderer.trimToWidth(logs.get(i), barWidth);
            this.drawString(drawContext, line, x, y, GuiTheme.TEXT_FAINT);
            y += 11;
        }
    }
}
