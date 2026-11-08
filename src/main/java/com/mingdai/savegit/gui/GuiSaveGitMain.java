package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.input.SaveGitKeyBindings;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.nio.file.Path;

/**
 * Litematica 风格的主界面：左上角标题 + 双列按钮。
 */
public class GuiSaveGitMain extends GuiBase {
    private static final int LEFT = 20;
    private static final int TOP = 40;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 28;
    private static final int COLUMN_GAP = 10;
    private static final int ROW_GAP = 8;

    private boolean worldAvailable;
    private String worldName = "";
    private int snapshotCount;
    private String statusMessage = "";

    public GuiSaveGitMain() {
        this.setTitle("SaveGit v" + SaveGitMod.MOD_VERSION + " 主菜单");
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int col1 = LEFT;
        int col2 = LEFT + BUTTON_WIDTH + COLUMN_GAP;
        int row1 = TOP;
        int row2 = TOP + BUTTON_HEIGHT + ROW_GAP;

        this.addButton(new ButtonGeneric(col1, row1, BUTTON_WIDTH, BUTTON_HEIGHT, "立即创建快照"),
                (button, mouseButton) -> this.onManualSnapshot());
        this.addButton(new ButtonGeneric(col1, row2, BUTTON_WIDTH, BUTTON_HEIGHT, "查看快照并恢复"),
                (button, mouseButton) -> GuiBase.openGui(new GuiSnapshotList()));
        this.addButton(new ButtonGeneric(col2, row1, BUTTON_WIDTH, BUTTON_HEIGHT, "设置"),
                (button, mouseButton) -> {
                    GuiSaveGitConfigs gui = new GuiSaveGitConfigs();
                    gui.setParent(this);
                    GuiBase.openGui(gui);
                });
        this.addButton(new ButtonGeneric(col2, row2, BUTTON_WIDTH, BUTTON_HEIGHT, "返回游戏"),
                (button, mouseButton) -> this.close());
    }

    private void onManualSnapshot() {
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

        int y = TOP + 2 * (BUTTON_HEIGHT + ROW_GAP) + 12;

        String worldLine = this.worldAvailable
                ? "当前世界: " + this.worldName
                : "当前未加载单人世界（仅支持集成服务端）";
        this.drawString(drawContext, worldLine, LEFT, y, this.worldAvailable ? 0xFFCCCCCC : 0xFFFFAA55);
        y += 12;

        if (this.worldAvailable) {
            this.drawString(drawContext, "快照数量: " + this.snapshotCount + "    最多保留: "
                    + SaveGitConfigs.maxSnapshots() + " 个", LEFT, y, 0xFFCCCCCC);
            y += 12;
        }

        this.drawString(drawContext, "总开关: " + onOff(SaveGitConfigs.isEnabled()),
                LEFT, y, 0xFFCCCCCC);
        y += 12;

        String autoLine = SaveGitConfigs.isScheduledSnapshotEnabled()
                ? "定时快照: 开（每 " + SaveGitConfigs.autoSnapshotIntervalMinutes() + " 分钟）"
                : "定时快照: 关";
        this.drawString(drawContext, autoLine, LEFT, y, 0xFFCCCCCC);
        y += 12;

        this.drawString(drawContext, "世界加载基线快照: "
                + onOff(SaveGitConfigs.isBaselineSnapshotEnabled()), LEFT, y, 0xFFCCCCCC);
        y += 16;

        if (!this.statusMessage.isEmpty()) {
            this.drawString(drawContext, this.statusMessage, LEFT, y, 0xFFFFDD55);
        }

        String keyName = SaveGitKeyBindings.OPEN_MAIN_MENU.getBoundKeyLocalizedText().getString();
        this.drawString(drawContext,
                "按键: " + keyName + "    可在 选项 - 控制 - 按键绑定 中修改",
                LEFT, this.height - 20, 0xFF9A9A9A);
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }
}
