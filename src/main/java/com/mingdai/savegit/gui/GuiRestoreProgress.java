package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.RestoreProgress;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;

import java.util.List;

public class GuiRestoreProgress extends GuiSaveGitScreen {
    private ButtonGeneric doneButton;

    public GuiRestoreProgress() {
        this.setTitle("正在恢复世界");
    }

    @Override
    public void initGui() {
        super.initGui();

        this.doneButton = new ButtonGeneric(this.width / 2 - 60, this.height - 40, 120, 20, "恢复中…");
        this.doneButton.setEnabled(false);
        this.addButton(this.doneButton, (button, mouseButton) -> this.mc.setScreen(new TitleScreen()));
    }

    @Override
    public String getTitleString() {
        if (RestoreProgress.isFailed()) {
            return "恢复失败";
        }
        if (RestoreProgress.isFinished()) {
            return "恢复完成";
        }
        return "正在恢复世界";
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        boolean failed = RestoreProgress.isFailed();
        boolean finished = RestoreProgress.isFinished();

        if (this.doneButton != null) {
            this.doneButton.setEnabled(finished);
            if (failed) {
                this.doneButton.setDisplayString("返回标题界面");
            } else if (finished) {
                this.doneButton.setDisplayString("完成");
            } else {
                this.doneButton.setDisplayString("恢复中…");
            }
        }

        int barX = 40;
        int barY = 72;
        int barWidth = this.width - 80;
        int barHeight = 12;
        int percent = RestoreProgress.getPercent();

        drawContext.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        drawContext.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        int filled = barWidth * percent / 100;
        drawContext.fill(barX, barY, barX + filled, barY + barHeight,
                failed ? 0xFFAA3333 : 0xFF3CB371);

        String progress = "进度: " + RestoreProgress.getCompleted() + "/" + RestoreProgress.getTotal()
                + "  (" + percent + "%)";
        this.drawString(drawContext, progress,
                this.width / 2 - this.getStringWidth(progress) / 2, barY + barHeight + 10, 0xFFFFFFFF);

        String detail;
        if (failed) {
            detail = "错误: " + safe(RestoreProgress.getErrorMessage());
        } else if (finished) {
            detail = "恢复完成，可以重新进入世界。";
        } else {
            detail = "当前文件: " + safe(RestoreProgress.getCurrentFile());
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
