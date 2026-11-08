package com.mingdai.savegit.gui;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.nio.file.Path;

public class GuiSaveGitMain extends GuiBase {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;

    private boolean worldAvailable;
    private String worldName = "";
    private int snapshotCount;
    private String statusMessage = "";

    public GuiSaveGitMain() {
        this.setTitle("SaveGit 主界面");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = this.height / 2 - 50;

        this.addButton(new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "立即创建快照"),
                (button, mouseButton) -> {
                    if (!this.worldAvailable) {
                        this.statusMessage = "当前未加载单人世界";
                        return;
                    }
                    if (!SaveGitConfigs.isEnabled()) {
                        this.statusMessage = "SaveGit 总开关已关闭";
                        return;
                    }
                    SaveGitSnapshotManager.manualSnapshot();
                    this.statusMessage = "已请求创建快照，请稍候…";
                });
        y += BUTTON_HEIGHT + BUTTON_GAP;

        this.addButton(new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "查看快照并恢复"),
                (button, mouseButton) -> GuiBase.openGui(new GuiSnapshotList()));
        y += BUTTON_HEIGHT + BUTTON_GAP;

        this.addButton(new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "设置"),
                (button, mouseButton) -> {
                    GuiSaveGitConfigs gui = new GuiSaveGitConfigs();
                    gui.setParent(this);
                    GuiBase.openGui(gui);
                });
        y += BUTTON_HEIGHT + BUTTON_GAP;

        this.addButton(new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "返回游戏"),
                (button, mouseButton) -> this.close());
    }

    private void reload() {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(this.mc);
        this.worldAvailable = worldDir != null;
        this.statusMessage = "";

        if (!this.worldAvailable) {
            this.worldName = "";
            this.snapshotCount = 0;
            return;
        }

        Path name = worldDir.getFileName();
        this.worldName = name == null ? "未知世界" : name.toString();
        this.snapshotCount = new SnapshotRepository(worldDir).listCommits().size();
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        int y = 34;
        String worldLine = this.worldAvailable
                ? "当前世界: " + this.worldName
                : "当前未加载单人世界（仅支持集成服务端）";
        this.drawString(drawContext, worldLine, 20, y, this.worldAvailable ? 0xFFCCCCCC : 0xFFFFAA55);
        y += 12;

        if (this.worldAvailable) {
            this.drawString(drawContext, "快照数量: " + this.snapshotCount, 20, y, 0xFFCCCCCC);
            y += 12;
        }

        String autoLine = SaveGitConfigs.isEnabled()
                ? "自动快照: 每 " + SaveGitConfigs.autoSnapshotIntervalMinutes() + " 分钟，最多保留 "
                    + SaveGitConfigs.maxSnapshots() + " 个"
                : "自动快照: 已关闭";
        this.drawString(drawContext, autoLine, 20, y, 0xFFCCCCCC);

        if (!this.statusMessage.isEmpty()) {
            int textY = this.height / 2 - 50 + 4 * (BUTTON_HEIGHT + BUTTON_GAP) + 8;
            this.drawString(drawContext, this.statusMessage,
                    this.width / 2 - this.getStringWidth(this.statusMessage) / 2, textY, 0xFFFFDD55);
        }
    }
}
