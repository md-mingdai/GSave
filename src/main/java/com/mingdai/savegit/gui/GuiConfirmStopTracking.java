package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 停止跟踪确认：删除该世界的 gsave 目录。
 */
public class GuiConfirmStopTracking extends GuiSaveGitScreen {
    private final Path worldDir;
    private final String worldDisplayName;
    private final Screen parent;

    public GuiConfirmStopTracking(Path worldDir, String worldDisplayName, Screen parent) {
        this.worldDir = worldDir;
        this.worldDisplayName = worldDisplayName;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.confirm.stop_tracking.title"));
    }

    @Override
    public void initGui() {
        super.initGui();

        int buttonWidth = 110;
        int y = this.panelBottom() + 18;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.confirm_delete")),
                (button, mouseButton) -> this.stopTracking());
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.goBack());
    }

    private void stopTracking() {
        try {
            SnapshotRepository.deleteAllData(this.worldDir);
            InfoUtils.sendVanillaMessage(
                    L10n.text("gsave.message.stop_tracking_done"));
        } catch (IOException e) {
            InfoUtils.sendVanillaMessage(
                    L10n.text("gsave.message.stop_tracking_failed", e.getMessage()));
        }
        this.goBack();
    }

    private void goBack() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            this.close();
        }
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            this.goBack();
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        lines.add(L10n.tr("gsave.confirm.stop_tracking.message.1", this.worldDisplayName));
        lines.add(L10n.tr("gsave.confirm.stop_tracking.message.2"));
        lines.add("");
        lines.add(L10n.tr("gsave.confirm.stop_tracking.message.3"));
        return lines;
    }

    private int panelStartY() {
        int count = this.buildLines().size();
        return this.height / 2 - count * 6 - 26;
    }

    private int panelBottom() {
        return this.panelStartY() + this.buildLines().size() * 12 + 10;
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = this.buildLines();
        int startY = this.panelStartY();
        GuiTheme.drawDialogPanel(drawContext, this.textRenderer, this.width / 2, startY, lines);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFF6060 : GuiTheme.TEXT_DIM;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }
    }
}
