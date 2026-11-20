package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.compat.ClientCompat;
import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.ChunkSelection;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.WorldRestorer;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.util.SnapshotTime;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 恢复确认界面：选择恢复内容（地形/实体/兴趣点/其他文件），
 * 可选择只恢复玩家附近指定半径的区块，并决定是否先创建安全快照。
 */
public class GuiConfirmRestore extends GuiSaveGitScreen {
    private static final int COLUMN_GAP = 24;
    private static final int ROW_GAP = 14;
    private static final int RADIUS_FIELD_WIDTH = 48;

    private final Commit commit;
    private final GuiSnapshotList parent;

    private boolean createSafetySnapshot = true;
    private boolean restoreRegion = true;
    private boolean restoreEntities = true;
    private boolean restorePoi = true;
    private boolean restoreWhole = true;
    private boolean chunkOnly;
    private String radiusText = "1";
    private String status = "";

    private WidgetCheckBoxSimple regionBox;
    private WidgetCheckBoxSimple entitiesBox;
    private WidgetCheckBoxSimple poiBox;
    private WidgetCheckBoxSimple otherBox;
    private WidgetCheckBoxSimple chunkBox;
    private WidgetCheckBoxSimple safetyCheckBox;
    private GuiTextFieldGeneric radiusField;

    public GuiConfirmRestore(Commit commit, GuiSnapshotList parent) {
        this.commit = commit;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.confirm_restore"));
    }

    @Override
    public void initGui() {
        super.initGui();

        int left = this.width / 2 - 150;
        int right = this.width / 2 + 40;

        this.regionBox = new WidgetCheckBoxSimple(left, this.row1Y(),
                L10n.tr("gsave.gui.checkbox.scope_region"), this.restoreRegion);
        this.regionBox.setChangeListener(() -> this.restoreRegion = this.regionBox.isChecked());
        this.poiBox = new WidgetCheckBoxSimple(left, this.row2Y(),
                L10n.tr("gsave.gui.checkbox.scope_poi"), this.restorePoi);
        this.poiBox.setChangeListener(() -> this.restorePoi = this.poiBox.isChecked());
        this.entitiesBox = new WidgetCheckBoxSimple(right, this.row1Y(),
                L10n.tr("gsave.gui.checkbox.scope_entities"), this.restoreEntities);
        this.entitiesBox.setChangeListener(() -> this.restoreEntities = this.entitiesBox.isChecked());
        this.otherBox = new WidgetCheckBoxSimple(right, this.row2Y(),
                L10n.tr("gsave.gui.checkbox.scope_other"), this.restoreWhole);
        this.otherBox.setChangeListener(() -> this.restoreWhole = this.otherBox.isChecked());
        this.addWidget(this.regionBox);
        this.addWidget(this.poiBox);
        this.addWidget(this.entitiesBox);
        this.addWidget(this.otherBox);

        this.chunkBox = new WidgetCheckBoxSimple(left, this.chunkY(),
                L10n.tr("gsave.gui.checkbox.chunk_only"), this.chunkOnly);
        this.chunkBox.setChangeListener(() -> this.chunkOnly = this.chunkBox.isChecked());
        this.addWidget(this.chunkBox);

        int centerX = this.width / 2;
        this.radiusField = new GuiTextFieldGeneric(centerX - RADIUS_FIELD_WIDTH / 2, this.radiusY(),
                RADIUS_FIELD_WIDTH, 20, this.font);
        this.radiusField.setMaxLength(2);
        this.radiusField.setTextWrapper(this.radiusText);
        this.addTextField(this.radiusField, field -> true);

        this.safetyCheckBox = new WidgetCheckBoxSimple(0, 0,
                L10n.tr("gsave.gui.checkbox.safety_snapshot"), this.createSafetySnapshot);
        this.safetyCheckBox.setPosition(centerX - this.safetyCheckBox.getWidth() / 2, this.safetyY());
        this.safetyCheckBox.setChangeListener(
                () -> this.createSafetySnapshot = this.safetyCheckBox.isChecked());
        this.addWidget(this.safetyCheckBox);

        int buttonWidth = 100;
        int y = this.buttonY();
        this.addButton(new ButtonGeneric(centerX - buttonWidth - 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.confirm_restore")),
                (button, mouseButton) -> this.confirm());
        this.addButton(new ButtonGeneric(centerX + 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.cancel());
    }

    private void confirm() {
        this.status = "";
        this.radiusText = this.radiusField.getTextWrapper().trim();

        if (!this.restoreRegion && !this.restoreEntities && !this.restorePoi && !this.restoreWhole) {
            this.status = L10n.tr("gsave.gui.message.content_empty");
            return;
        }

        ChunkSelection selection = null;
        if (this.chunkOnly) {
            if (!this.restoreRegion && !this.restoreEntities && !this.restorePoi) {
                this.status = L10n.tr("gsave.gui.message.chunk_need_type");
                return;
            }
            Integer radius = this.parseRadius();
            if (radius == null) {
                this.status = L10n.tr("gsave.gui.message.chunk_radius_invalid",
                        String.valueOf(SaveGitSnapshotManager.MAX_CHUNK_RADIUS));
                return;
            }
            selection = SaveGitSnapshotManager.selectionAroundPlayer(this.mc, radius);
            if (selection == null) {
                this.status = L10n.tr("gsave.gui.message.no_player");
                return;
            }
        }

        WorldRestorer.RestoreFilter filter = new WorldRestorer.RestoreFilter(
                this.restoreRegion, this.restoreEntities, this.restorePoi,
                this.chunkOnly ? false : this.restoreWhole);
        ChunkSelection chunkSelection = selection;
        Runnable start = () -> {
            ClientCompat.setScreen(this.mc, null);
            SaveGitSnapshotManager.requestRestore(this.mc, this.commit,
                    this.createSafetySnapshot, chunkSelection, filter);
        };

        if (this.createSafetySnapshot) {
            start.run();
        } else {
            GuiBase.openGui(new GuiConfirmNoSafetyRestore(this.commit, this, start));
        }
    }

    private Integer parseRadius() {
        int radius;
        try {
            radius = Integer.parseInt(this.radiusText);
        } catch (NumberFormatException e) {
            return null;
        }
        return radius >= 1 && radius <= SaveGitSnapshotManager.MAX_CHUNK_RADIUS ? radius : null;
    }

    @Override
    public boolean onEscapePressed() {
        this.cancel();
        return true;
    }

    private void cancel() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            GuiBase.openGui(new GuiSnapshotList());
        }
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        lines.add(L10n.tr("gsave.gui.message.restore_target", this.commit.shortId()));
        lines.add(L10n.tr("gsave.gui.label.time", SnapshotTime.full(this.commit.timestamp)));
        if (this.commit.durationMs >= 0) {
            lines.add(L10n.tr("gsave.gui.label.duration",
                    SnapshotTime.duration(this.commit.durationMs)));
        }
        lines.add(L10n.tr("gsave.gui.label.message", L10n.commitMessage(this.commit.message)));
        if (this.commit.mcVersion != null && !this.commit.mcVersion.isEmpty()
                && !this.commit.mcVersion.equals(SaveGitMod.MC_VERSION)) {
            lines.add(L10n.tr("gsave.gui.message.version_mismatch",
                    this.commit.mcVersion, SaveGitMod.MC_VERSION));
        }
        lines.add("");
        lines.add(L10n.tr("gsave.gui.message.safety_hint"));
        lines.add(L10n.tr("gsave.gui.message.restore_warning"));
        return lines;
    }

    private int panelStartY() {
        return this.height / 2 - this.buildLines().size() * 6 - 92;
    }

    private int panelBottom() {
        return this.panelStartY() + this.buildLines().size() * 12 + 10;
    }

    private int contentLabelY() {
        return this.panelBottom() + 8;
    }

    private int row1Y() {
        return this.contentLabelY() + 12;
    }

    private int row2Y() {
        return this.row1Y() + ROW_GAP;
    }

    private int chunkY() {
        return this.row2Y() + 20;
    }

    private int radiusY() {
        return this.chunkY() + 18;
    }

    private int safetyY() {
        return this.radiusY() + 24;
    }

    private int buttonY() {
        return this.safetyY() + 24;
    }

    @Override
    protected void drawContents(GuiContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = this.buildLines();
        int startY = this.panelStartY();
        GuiTheme.drawDialogPanel(drawContext, this.font, this.width / 2, startY, lines);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFFFFFF : 0xFFB0B0B0;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }

        String contentLabel = L10n.tr("gsave.gui.label.restore_content");
        this.drawString(drawContext, contentLabel,
                this.width / 2 - this.getStringWidth(contentLabel) / 2,
                this.contentLabelY(), GuiTheme.ACCENT);

        String radiusLabel = L10n.tr("gsave.gui.label.chunk_radius");
        this.drawString(drawContext, radiusLabel,
                this.width / 2 - RADIUS_FIELD_WIDTH / 2 - 8 - this.getStringWidth(radiusLabel),
                this.radiusY() + 6, GuiTheme.TEXT_DIM);

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
            color = this.chunkOnly ? GuiTheme.TEXT_DIM : GuiTheme.TEXT_FAINT;
        }
        this.drawString(drawContext, preview,
                this.width / 2 + RADIUS_FIELD_WIDTH / 2 + 10, this.radiusY() + 6, color);

        if (!this.status.isEmpty()) {
            this.drawString(drawContext, this.status,
                    this.width / 2 - this.getStringWidth(this.status) / 2,
                    this.buttonY() + 26, GuiTheme.DANGER);
        }
    }
}
