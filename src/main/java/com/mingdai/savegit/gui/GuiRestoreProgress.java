package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.RestoreProgress;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

import java.util.List;

public class GuiRestoreProgress extends GuiSaveGitScreen {
    private ButtonGeneric doneButton;
    private ButtonGeneric worldListButton;

    public GuiRestoreProgress() {
        this.setTitle(L10n.tr("gsave.progress.title.restoring"));
    }

    @Override
    public void initGui() {
        super.initGui();

        this.doneButton = new ButtonGeneric(this.width / 2 - 60, this.height - 40, 120, 20,
                L10n.tr("gsave.progress.button.restoring"));
        this.doneButton.setEnabled(false);
        this.addButton(this.doneButton, (button, mouseButton) -> this.mc.setScreen(new TitleScreen()));

        this.worldListButton = new ButtonGeneric(this.width / 2 - 60, this.height - 66, 120, 20,
                L10n.tr("gsave.button.back_to_world_list"));
        this.worldListButton.setEnabled(false);
        this.addButton(this.worldListButton,
                (button, mouseButton) -> this.mc.setScreen(new SelectWorldScreen(new TitleScreen())));
    }

    @Override
    public String getTitleString() {
        if (RestoreProgress.isFailed()) {
            return L10n.tr("gsave.progress.title.failed");
        }
        if (RestoreProgress.isFinished()) {
            return L10n.tr("gsave.progress.title.finished");
        }
        return L10n.tr("gsave.progress.title.restoring");
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        boolean failed = RestoreProgress.isFailed();
        boolean finished = RestoreProgress.isFinished();

        if (this.doneButton != null) {
            this.doneButton.setEnabled(finished);
            if (this.worldListButton != null) {
                this.worldListButton.setEnabled(finished);
            }
            if (failed) {
                this.doneButton.setDisplayString(L10n.tr("gsave.button.back_to_title"));
            } else if (finished) {
                this.doneButton.setDisplayString(L10n.tr("gsave.button.done"));
            } else {
                this.doneButton.setDisplayString(L10n.tr("gsave.progress.button.restoring"));
            }
        }

        int barX = 40;
        int barY = 72;
        int barWidth = this.width - 80;
        int barHeight = 12;
        int percent = RestoreProgress.getPercent();

        GuiTheme.drawPanel(drawContext, barX - 16, barY - 16,
                barX + barWidth + 16, this.height - 78);
        drawContext.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        drawContext.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF1A1E23);
        int filled = barWidth * percent / 100;
        drawContext.fill(barX, barY, barX + filled, barY + barHeight,
                failed ? GuiTheme.DANGER : GuiTheme.ACCENT);

        String progress = L10n.tr("gsave.progress.label.progress",
                String.valueOf(RestoreProgress.getCompleted()),
                String.valueOf(RestoreProgress.getTotal()))
                + "  (" + percent + "%)";
        this.drawString(drawContext, progress,
                this.width / 2 - this.getStringWidth(progress) / 2, barY + barHeight + 10, 0xFFFFFFFF);

        String detail;
        if (failed) {
            detail = L10n.tr("gsave.progress.label.error", safe(RestoreProgress.getErrorMessage()));
        } else if (finished) {
            detail = L10n.tr("gsave.progress.message.finished");
        } else {
            detail = L10n.tr("gsave.progress.label.current_file",
                    safe(RestoreProgress.getCurrentFile()));
        }
        detail = this.textRenderer.trimToWidth(detail, this.width - 80);
        this.drawString(drawContext, detail,
                this.width / 2 - this.getStringWidth(detail) / 2, barY + barHeight + 26,
                failed ? 0xFFFF6060 : 0xFFCCCCCC);

        List<String> logs = RestoreProgress.getLogs();
        int logLines = 8;
        int start = Math.max(0, logs.size() - logLines);
        int logY = barY + barHeight + 50;
        for (int i = start; i < logs.size(); i++) {
            String line = this.textRenderer.trimToWidth(logs.get(i), this.width - 80);
            this.drawString(drawContext, line, 40, logY, 0xFF909090);
            logY += 12;
        }
    }

    @Override
    public void close() {
        if (RestoreProgress.isFinished()) {
            this.mc.setScreen(new TitleScreen());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
