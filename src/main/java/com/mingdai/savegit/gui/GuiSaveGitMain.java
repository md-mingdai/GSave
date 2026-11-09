package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.config.SnapshotPriority;
import com.mingdai.savegit.input.SaveGitKeyBindings;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.snapshot.SnapshotStatus;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.nio.file.Path;

/**
 * 主界面：左上角标题 + 双列按钮 + 状态面板。
 */
public class GuiSaveGitMain extends GuiSaveGitScreen {
    private static final int LEFT = 20;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int ROW_TOP = 44;
    private static final int BUTTON_ROWS = 4;
    private static final int PANEL_TOP = ROW_TOP + BUTTON_ROWS * (BUTTON_HEIGHT + ROW_GAP) + 8;
    private static final int PANEL_BOTTOM_MARGIN = 10;
    private static final int LABEL_COLOR = 0xFF9A9A9A;
    private static final int VALUE_COLOR = 0xFFFFFFFF;

    private boolean worldAvailable;
    private String worldName = "";
    private int snapshotCount;
    private String statusMessage = "";
    private final Screen parentScreen;

    public GuiSaveGitMain() {
        this(null);
    }

    public GuiSaveGitMain(Screen parentScreen) {
        this.parentScreen = parentScreen;
        this.setParent(parentScreen);
        this.setTitle("GSave v" + SaveGitMod.MOD_VERSION + " 主菜单");
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int x = LEFT;
        this.addLeftButton(x, ROW_TOP, "立即创建快照", this::onManualSnapshot);
        this.addLeftButton(x, ROW_TOP + (BUTTON_HEIGHT + ROW_GAP), "查看快照并恢复",
                () -> GuiBase.openGui(new GuiSnapshotList()));
        this.addLeftButton(x, ROW_TOP + (BUTTON_HEIGHT + ROW_GAP) * 2, "设置", () -> {
            GuiSaveGitConfigs gui = new GuiSaveGitConfigs();
            gui.setParent(this);
            GuiBase.openGui(gui);
        });
        this.addLeftButton(x, ROW_TOP + (BUTTON_HEIGHT + ROW_GAP) * 3,
                this.parentScreen == null ? "返回游戏" : "返回", this::goBack);
    }

    private void goBack() {
        if (this.parentScreen != null) {
            GuiBase.openGui(this.parentScreen);
        } else {
            this.close();
        }
    }

    private void addLeftButton(int x, int y, String text, Runnable action) {
        ButtonGeneric button = new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, text);
        button.setTextCentered(true);
        this.addButton(button, (mouseButton, button1) -> action.run());
    }

    private void onManualSnapshot() {
        if (!this.worldAvailable) {
            this.statusMessage = "当前未加载单人世界";
            return;
        }
        if (!SaveGitConfigs.isEnabled()) {
            this.statusMessage = "GSave 总开关已关闭";
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

        this.worldName = SaveGitSnapshotManager.getWorldName(this.mc);
        this.snapshotCount = new SnapshotRepository(worldDir).listCommits().size();
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        this.drawStringWithShadow(drawContext, "存档快照与回滚", LEFT, 26, 0xFF808080);

        boolean snapshotActive = SnapshotStatus.isActive();
        int lines = 5 + (snapshotActive ? 1 : 0) + (this.statusMessage.isEmpty() ? 0 : 1);
        int panelHeight = 10 + lines * 12 + 6;
        // 信息栏固定贴底，窗口过矮时才退回按钮下方
        int panelTop = Math.max(PANEL_TOP, this.height - PANEL_BOTTOM_MARGIN - panelHeight);
        drawContext.fill(LEFT, panelTop, this.width - LEFT, panelTop + panelHeight, 0x70000000);
        drawContext.fill(LEFT, panelTop, this.width - LEFT, panelTop + 1, 0x40FFFFFF);
        drawContext.fill(LEFT, panelTop + panelHeight - 1, this.width - LEFT, panelTop + panelHeight, 0x40FFFFFF);

        int x = LEFT + 8;
        int y = panelTop + 7;

        String world = this.worldAvailable ? this.worldName : "未加载单人世界";
        this.drawLabelValue(drawContext, x, y, "当前世界：", world,
                this.worldAvailable ? VALUE_COLOR : 0xFFFFAA55);
        y += 12;

        String count = this.worldAvailable ? String.valueOf(this.snapshotCount) : "—";
        String limit = SaveGitConfigs.isUnlimitedSnapshots()
                ? "无限" : SaveGitConfigs.maxSnapshots() + " 个";
        this.drawLabelValue(drawContext, x, y, "快照数量：", count + "    保留上限：" + limit, VALUE_COLOR);
        y += 12;

        String scheduled = SaveGitConfigs.isScheduledSnapshotEnabled()
                ? "开（每 " + SaveGitConfigs.autoSnapshotIntervalMinutes() + " 分钟）" : "关";
        this.drawLabelValue(drawContext, x, y, "总开关：",
                onOff(SaveGitConfigs.isEnabled()) + "    定时快照：" + scheduled, VALUE_COLOR);
        y += 12;

        SnapshotPriority priority = SaveGitConfigs.snapshotPriority();
        String priorityText = priority == SnapshotPriority.GAMEPLAY ? "游戏体验优先" : "保存速度优先";
        this.drawLabelValue(drawContext, x, y, "基线快照：",
                onOff(SaveGitConfigs.isBaselineSnapshotEnabled())
                        + "    断开快照：" + onOff(SaveGitConfigs.isDisconnectSnapshotEnabled()),
                VALUE_COLOR);
        y += 12;

        this.drawLabelValue(drawContext, x, y, "执行优先级：", priorityText, VALUE_COLOR);
        y += 12;

        if (snapshotActive) {
            String label = SnapshotStatus.getLabel();
            String line = label.isEmpty() ? "快照进行中…" : "快照进行中…（" + label + "）";
            this.drawStringWithShadow(drawContext, line, x, y, 0xFFFFDD55);
            y += 12;
        }

        if (!this.statusMessage.isEmpty()) {
            this.drawStringWithShadow(drawContext, this.statusMessage, x, y, 0xFFFFDD55);
        }

        String keyName = SaveGitKeyBindings.OPEN_MAIN_MENU.getBoundKeyLocalizedText().getString();
        this.drawStringWithShadow(drawContext,
                "按键：" + keyName + "    可在 选项 - 控制 - 按键绑定 中修改",
                LEFT, panelTop - 12, 0xFF9A9A9A);
    }

    private void drawLabelValue(DrawContext drawContext, int x, int y, String label, String value, int valueColor) {
        this.drawStringWithShadow(drawContext, label, x, y, LABEL_COLOR);
        this.drawStringWithShadow(drawContext, value, x + this.getStringWidth(label), y, valueColor);
    }

    private static String onOff(boolean value) {
        return value ? "开" : "关";
    }
}
