package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.config.SnapshotPriority;
import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.input.SaveGitKeyBindings;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.snapshot.SnapshotStatus;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.nio.file.Path;

/**
 * 主界面：左上角标题 + 单列按钮 + 贴底状态面板。
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
        this.setTitle(L10n.tr("gsave.gui.main.title", SaveGitMod.MOD_VERSION));
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
        this.addLeftButton(x, ROW_TOP, L10n.tr("gsave.gui.button.create_now"), this::onManualSnapshot);
        this.addLeftButton(x, ROW_TOP + (BUTTON_HEIGHT + ROW_GAP),
                L10n.tr("gsave.gui.button.browse_snapshots"),
                () -> GuiBase.openGui(new GuiSnapshotList()));
        this.addLeftButton(x, ROW_TOP + (BUTTON_HEIGHT + ROW_GAP) * 2,
                L10n.tr("gsave.gui.button.settings"), () -> {
                    GuiSaveGitConfigs gui = new GuiSaveGitConfigs();
                    gui.setParent(this);
                    GuiBase.openGui(gui);
                });
        this.addLeftButton(x, ROW_TOP + (BUTTON_HEIGHT + ROW_GAP) * 3,
                L10n.tr(this.parentScreen == null
                        ? "gsave.button.back_to_game" : "gsave.button.back"),
                this::goBack);
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
            this.statusMessage = L10n.tr("gsave.gui.message.world_unloaded");
            return;
        }
        if (!SaveGitConfigs.isEnabled()) {
            this.statusMessage = L10n.tr("gsave.gui.message.disabled");
            return;
        }
        SaveGitSnapshotManager.manualSnapshot();
        this.statusMessage = L10n.tr("gsave.gui.message.snapshot_requested");
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

        GuiTheme.drawHeader(drawContext, 0, 0, this.width, 32);
        this.drawStringWithShadow(drawContext, L10n.tr("gsave.gui.main.subtitle"),
                LEFT, 22, GuiTheme.TEXT_DIM);

        boolean snapshotActive = SnapshotStatus.isActive();
        int lines = 5 + (snapshotActive ? 1 : 0) + (this.statusMessage.isEmpty() ? 0 : 1);
        int panelHeight = 10 + lines * 12 + 6;
        // 信息栏固定贴底，窗口过矮时才退回按钮下方
        int panelTop = Math.max(PANEL_TOP, this.height - PANEL_BOTTOM_MARGIN - panelHeight);
        GuiTheme.drawPanel(drawContext, LEFT, panelTop, this.width - LEFT, panelTop + panelHeight);

        int x = LEFT + 8;
        int y = panelTop + 7;

        String world = this.worldAvailable
                ? this.worldName : L10n.tr("gsave.gui.value.world_unloaded");
        this.drawLabelValue(drawContext, x, y, L10n.tr("gsave.gui.label.world"), world,
                this.worldAvailable ? VALUE_COLOR : 0xFFFFAA55);
        y += 12;

        String count = this.worldAvailable ? String.valueOf(this.snapshotCount) : "—";
        String limit = SaveGitConfigs.isUnlimitedSnapshots()
                ? L10n.tr("gsave.gui.value.unlimited")
                : L10n.tr("gsave.gui.value.count", String.valueOf(SaveGitConfigs.maxSnapshots()));
        this.drawLabelValue(drawContext, x, y,
                L10n.tr("gsave.gui.label.snapshot_count"),
                L10n.tr("gsave.gui.value.count_and_limit", count, limit), VALUE_COLOR);
        y += 12;

        String scheduled = SaveGitConfigs.isScheduledSnapshotEnabled()
                ? L10n.tr("gsave.gui.value.scheduled_on",
                        String.valueOf(SaveGitConfigs.autoSnapshotIntervalMinutes()))
                : L10n.tr("gsave.gui.value.off");
        this.drawLabelValue(drawContext, x, y, L10n.tr("gsave.gui.label.enabled"),
                onOff(SaveGitConfigs.isEnabled()) + "    "
                        + L10n.tr("gsave.gui.label.scheduled") + scheduled, VALUE_COLOR);
        y += 12;

        SnapshotPriority priority = SaveGitConfigs.snapshotPriority();
        String priorityText = L10n.tr(priority == SnapshotPriority.GAMEPLAY
                ? "gsave.config.value.gameplay" : "gsave.config.value.speed");
        this.drawLabelValue(drawContext, x, y, L10n.tr("gsave.gui.label.baseline"),
                onOff(SaveGitConfigs.isBaselineSnapshotEnabled()) + "    "
                        + L10n.tr("gsave.gui.label.disconnect")
                        + onOff(SaveGitConfigs.isDisconnectSnapshotEnabled()),
                VALUE_COLOR);
        y += 12;

        this.drawLabelValue(drawContext, x, y, L10n.tr("gsave.gui.label.priority"),
                priorityText, VALUE_COLOR);
        y += 12;

        if (snapshotActive) {
            String label = L10n.commitMessage(SnapshotStatus.getLabel());
            String line = label.isEmpty()
                    ? L10n.tr("gsave.gui.message.snapshot_active")
                    : L10n.tr("gsave.gui.message.snapshot_active_reason", label);
            this.drawStringWithShadow(drawContext, line, x, y, 0xFFFFDD55);
            y += 12;
        }

        if (!this.statusMessage.isEmpty()) {
            this.drawStringWithShadow(drawContext, this.statusMessage, x, y, 0xFFFFDD55);
        }

        String keyName = SaveGitKeyBindings.OPEN_MAIN_MENU.getBoundKeyLocalizedText().getString();
        this.drawStringWithShadow(drawContext,
                L10n.tr("gsave.gui.hint.keybind", keyName),
                LEFT, panelTop - 12, 0xFF9A9A9A);
    }

    private void drawLabelValue(DrawContext drawContext, int x, int y, String label, String value, int valueColor) {
        this.drawStringWithShadow(drawContext, label, x, y, LABEL_COLOR);
        this.drawStringWithShadow(drawContext, value, x + this.getStringWidth(label), y, valueColor);
    }

    private static String onOff(boolean value) {
        return L10n.tr(value ? "gsave.gui.value.on" : "gsave.gui.value.off");
    }
}
