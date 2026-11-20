package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.ChunkSelection;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块恢复确认界面：指定以玩家为中心的恢复半径与是否创建安全快照。
 */
public class GuiConfirmChunkRestore extends GuiSaveGitScreen {
    private static final int FIELD_WIDTH = 64;
    private static final int BUTTON_WIDTH = 110;

    private final Commit commit;
    private final GuiSnapshotList parent;
    private GuiTextFieldGeneric radiusField;
    private WidgetCheckBoxSimple safetyCheckBox;
    private boolean createSafetySnapshot = true;
    private String status = "";

    public GuiConfirmChunkRestore(Commit commit, GuiSnapshotList parent) {
        this.commit = commit;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.chunk_restore"));
    }

    @Override
    public void initGui() {
        super.initGui();

        int centerX = this.width / 2;
        this.radiusField = new GuiTextFieldGeneric(centerX - FIELD_WIDTH / 2, this.fieldY(),
                FIELD_WIDTH, 20, this.textRenderer);
        this.radiusField.setMaxLength(2);
        this.radiusField.setTextWrapper("1");
        this.addTextField(this.radiusField, field -> true);

        this.safetyCheckBox = new WidgetCheckBoxSimple(0, 0,
                L10n.tr("gsave.gui.checkbox.safety_snapshot"), true);
        this.safetyCheckBox.setPosition(centerX - this.safetyCheckBox.getWidth() / 2,
                this.checkboxY());
        this.safetyCheckBox.setChangeListener(
                () -> this.createSafetySnapshot = this.safetyCheckBox.isChecked());
        this.addWidget(this.safetyCheckBox);

        int y = this.buttonY();
        this.addButton(new ButtonGeneric(centerX - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, 20,
                        L10n.tr("gsave.button.confirm_restore")),
                (button, mouseButton) -> this.startRestore());
        this.addButton(new ButtonGeneric(centerX + 4, y, BUTTON_WIDTH, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.goBack());
    }

    private void startRestore() {
        Integer radius = this.parseRadius();
        if (radius == null) {
            // 预览区已显示错误提示
            return;
        }
        ChunkSelection selection = SaveGitSnapshotManager.selectionAroundPlayer(this.mc, radius);
        if (selection == null) {
            this.status = L10n.tr("gsave.gui.message.no_player");
            return;
        }
        this.mc.setScreen(null);
        SaveGitSnapshotManager.requestRestore(this.mc, this.commit, this.createSafetySnapshot, selection);
    }

    private void goBack() {
        GuiBase.openGui(this.parent);
    }

    @Override
    protected boolean onEscapePressed() {
        this.goBack();
        return true;
    }

    private Integer parseRadius() {
        int radius;
        try {
            radius = Integer.parseInt(this.radiusField.getTextWrapper().trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return radius >= 1 && radius <= SaveGitSnapshotManager.MAX_CHUNK_RADIUS ? radius : null;
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        lines.add(L10n.tr("gsave.gui.message.chunk_restore_target", this.commit.shortId()));
        String message = this.commit.note != null && !this.commit.note.isEmpty()
                ? this.commit.note
                : L10n.commitMessage(this.commit.message);
        lines.add(L10n.tr("gsave.gui.label.message", message));
        String player = SaveGitSnapshotManager.describePlayerChunk(this.mc);
        lines.add(player == null ? L10n.tr("gsave.gui.message.no_player") : player);
        lines.add("");
        lines.add(L10n.tr("gsave.gui.message.chunk_restore_hint",
                String.valueOf(SaveGitSnapshotManager.MAX_CHUNK_RADIUS)));
        lines.add(L10n.tr("gsave.gui.message.chunk_restore_scope"));
        return lines;
    }

    private int panelStartY() {
        return this.height / 2 - this.buildLines().size() * 6 - 76;
    }

    private int panelBottom() {
        return this.panelStartY() + this.buildLines().size() * 12 + 10;
    }

    private int fieldY() {
        return this.panelBottom() + 10;
    }

    private int previewY() {
        return this.fieldY() + 24;
    }

    private int checkboxY() {
        return this.previewY() + 14;
    }

    private int buttonY() {
        return this.checkboxY() + 24;
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = this.buildLines();
        int startY = this.panelStartY();
        GuiTheme.drawDialogPanel(drawContext, this.textRenderer, this.width / 2, startY, lines);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFFFFFF : 0xFFB0B0B0;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }

        String label = L10n.tr("gsave.gui.label.chunk_radius");
        this.drawString(drawContext, label,
                this.width / 2 - FIELD_WIDTH / 2 - 8 - this.getStringWidth(label),
                this.fieldY() + 6, GuiTheme.TEXT_DIM);

        Integer radius = this.parseRadius();
        String preview;
        int color;
        if (radius == null) {
            preview = L10n.tr("gsave.gui.message.chunk_radius_invalid",
                    String.valueOf(SaveGitSnapshotManager.MAX_CHUNK_RADIUS));
            color = GuiTheme.DANGER;
        } else {
            int side = radius * 2 - 1;
            preview = L10n.tr("gsave.gui.message.chunk_restore_preview",
                    String.valueOf(side), String.valueOf(side), String.valueOf((long) side * side));
            color = GuiTheme.TEXT_DIM;
        }
        this.drawString(drawContext, preview,
                this.width / 2 - this.getStringWidth(preview) / 2, this.previewY(), color);

        if (!this.status.isEmpty()) {
            this.drawString(drawContext, this.status,
                    this.width / 2 - this.getStringWidth(this.status) / 2,
                    this.buttonY() + 26, GuiTheme.DANGER);
        }
    }
}
