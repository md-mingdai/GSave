package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.preview.PreviewManager;
import com.mingdai.savegit.preview.PreviewSession;
import com.mingdai.savegit.snapshot.ChunkSelection;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.WorldRestorer;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.util.SnapshotTime;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 恢复确认界面：选择恢复内容（地形/实体/兴趣点/数据包/其他文件），
 * 可选择只恢复玩家附近指定半径的区块、是否删除快照中不存在的新增文件，
 * 以及是否先创建安全快照。
 */
public class GuiConfirmRestore extends GuiSaveGitScreen {
    private static final int ROW_GAP = 14;
    private static final int RADIUS_FIELD_WIDTH = 48;

    private final Commit commit;
    private final GuiSnapshotList parent;

    private boolean createSafetySnapshot = true;
    private boolean restoreRegion = true;
    private boolean restoreEntities = true;
    private boolean restorePoi = true;
    private boolean restoreWhole = true;
    private boolean restoreDatapacks = true;
    private boolean chunkOnly;
    private boolean deleteNewFiles = SaveGitConfigs.isBetaDeleteNewFilesEnabled();
    private boolean warnedMissingMods;
    private String radiusText = "1";
    private String status = "";

    private WidgetCheckBoxSimple regionBox;
    private WidgetCheckBoxSimple entitiesBox;
    private WidgetCheckBoxSimple poiBox;
    private WidgetCheckBoxSimple otherBox;
    private WidgetCheckBoxSimple datapacksBox;
    private WidgetCheckBoxSimple chunkBox;
    private WidgetCheckBoxSimple safetyCheckBox;
    private WidgetCheckBoxSimple deleteNewBox;
    private GuiTextFieldGeneric radiusField;

    public GuiConfirmRestore(Commit commit, GuiSnapshotList parent) {
        this.commit = commit;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.confirm_restore"));
    }

    private boolean showDeleteNewFiles() {
        return SaveGitConfigs.hasBetaFileGroups();
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
        this.datapacksBox = new WidgetCheckBoxSimple(left, this.row3Y(),
                L10n.tr("gsave.gui.checkbox.scope_datapacks"), this.restoreDatapacks);
        this.datapacksBox.setChangeListener(
                () -> this.restoreDatapacks = this.datapacksBox.isChecked());
        this.addWidget(this.regionBox);
        this.addWidget(this.poiBox);
        this.addWidget(this.entitiesBox);
        this.addWidget(this.otherBox);
        this.addWidget(this.datapacksBox);

        this.chunkBox = new WidgetCheckBoxSimple(left, this.chunkY(),
                L10n.tr("gsave.gui.checkbox.chunk_only"), this.chunkOnly);
        this.chunkBox.setChangeListener(() -> this.chunkOnly = this.chunkBox.isChecked());
        this.addWidget(this.chunkBox);

        int centerX = this.width / 2;
        this.radiusField = new GuiTextFieldGeneric(centerX - RADIUS_FIELD_WIDTH / 2, this.radiusY(),
                RADIUS_FIELD_WIDTH, 20, this.textRenderer);
        this.radiusField.setMaxLength(2);
        this.radiusField.setTextWrapper(this.radiusText);
        this.addTextField(this.radiusField, field -> true);

        this.safetyCheckBox = new WidgetCheckBoxSimple(0, 0,
                L10n.tr("gsave.gui.checkbox.safety_snapshot"), this.createSafetySnapshot);
        this.safetyCheckBox.setPosition(centerX - this.safetyCheckBox.getWidth() / 2, this.safetyY());
        this.safetyCheckBox.setChangeListener(
                () -> this.createSafetySnapshot = this.safetyCheckBox.isChecked());
        this.addWidget(this.safetyCheckBox);

        if (this.showDeleteNewFiles()) {
            this.deleteNewBox = new WidgetCheckBoxSimple(0, 0,
                    L10n.tr("gsave.gui.checkbox.delete_new_files"), this.deleteNewFiles);
            this.deleteNewBox.setPosition(centerX - this.deleteNewBox.getWidth() / 2, this.deleteY());
            this.deleteNewBox.setChangeListener(
                    () -> this.deleteNewFiles = this.deleteNewBox.isChecked());
            this.addWidget(this.deleteNewBox);
        }

        boolean previewable = this.canPreview();
        int buttonWidth = previewable ? 96 : 100;
        int gap = 6;
        int count = previewable ? 3 : 2;
        int totalWidth = buttonWidth * count + gap * (count - 1);
        int buttonX = centerX - totalWidth / 2;
        int y = this.buttonY();

        if (previewable) {
            this.addButton(new ButtonGeneric(buttonX, y, buttonWidth, 20,
                            L10n.tr("gsave.button.preview")),
                    (button, mouseButton) -> this.preview());
            buttonX += buttonWidth + gap;
        }
        this.addButton(new ButtonGeneric(buttonX, y, buttonWidth, 20,
                        L10n.tr("gsave.button.confirm_restore")),
                (button, mouseButton) -> this.confirm());
        buttonX += buttonWidth + gap;
        this.addButton(new ButtonGeneric(buttonX, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.cancel());
    }

    private boolean canPreview() {
        return this.mc != null && this.mc.player != null && this.mc.world != null
                && SaveGitSnapshotManager.getWorldDirectory(this.mc) != null;
    }

    private void preview() {
        RestoreRequest request = this.buildRequest(true);
        if (request == null) {
            return;
        }
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(this.mc);
        if (worldDir == null) {
            this.status = L10n.tr("gsave.gui.message.preview_no_world");
            return;
        }
        PreviewSession session = new PreviewSession(this.commit, worldDir,
                request.filter(), request.selection(), request.deleteNew());
        if (!PreviewManager.getInstance().start(this.mc, session, this)) {
            this.status = L10n.tr("gsave.gui.message.preview_no_world");
        }
    }

    private void confirm() {
        RestoreRequest request = this.buildRequest(false);
        if (request == null) {
            return;
        }
        ChunkSelection chunkSelection = request.selection();
        WorldRestorer.RestoreFilter filter = request.filter();
        boolean deleteNew = request.deleteNew();
        Runnable start = () -> {
            this.mc.setScreen(null);
            SaveGitSnapshotManager.requestRestore(this.mc, this.commit,
                    this.createSafetySnapshot, chunkSelection, filter, deleteNew);
        };

        if (this.createSafetySnapshot) {
            start.run();
        } else {
            GuiBase.openGui(new GuiConfirmNoSafetyRestore(this.commit, this, start));
        }
    }

    private RestoreRequest buildRequest(boolean forPreview) {
        this.status = "";
        this.radiusText = this.radiusField.getTextWrapper().trim();

        if (!this.restoreRegion && !this.restoreEntities && !this.restorePoi
                && !this.restoreWhole && !this.restoreDatapacks) {
            this.status = L10n.tr("gsave.gui.message.content_empty");
            return null;
        }

        ChunkSelection selection = null;
        if (this.chunkOnly) {
            if (!this.restoreRegion && !this.restoreEntities && !this.restorePoi) {
                this.status = L10n.tr("gsave.gui.message.chunk_need_type");
                return null;
            }
            Integer radius = this.parseRadius();
            if (radius == null) {
                this.status = L10n.tr("gsave.gui.message.chunk_radius_invalid",
                        String.valueOf(SaveGitSnapshotManager.MAX_CHUNK_RADIUS));
                return null;
            }
            selection = SaveGitSnapshotManager.selectionAroundPlayer(this.mc, radius);
            if (selection == null) {
                this.status = L10n.tr("gsave.gui.message.no_player");
                return null;
            }
        }

        // Beta:恢复前提示快照里存在、当前缺失的模组;首次点击只警告,再次点击继续
        if (!forPreview && !this.warnedMissingMods && SaveGitConfigs.isBetaModEnvironmentEnabled()) {
            Path worldDir = SaveGitSnapshotManager.getWorldDirectory(this.mc);
            List<String> missing = SaveGitSnapshotManager.missingMods(worldDir, this.commit);
            if (!missing.isEmpty()) {
                this.warnedMissingMods = true;
                this.status = L10n.tr("gsave.gui.message.missing_mods", joinMissing(missing));
                return null;
            }
        }

        boolean chunkMode = this.chunkOnly;
        WorldRestorer.RestoreFilter filter = new WorldRestorer.RestoreFilter(
                this.restoreRegion, this.restoreEntities, this.restorePoi,
                chunkMode ? false : this.restoreWhole,
                chunkMode ? false : this.restoreDatapacks);
        boolean deleteNew = this.deleteNewFiles && this.showDeleteNewFiles() && !chunkMode;
        return new RestoreRequest(selection, filter, deleteNew);
    }

    private record RestoreRequest(ChunkSelection selection,
                                  WorldRestorer.RestoreFilter filter,
                                  boolean deleteNew) {
    }

    private static String joinMissing(List<String> missing) {
        int limit = Math.min(5, missing.size());
        String joined = String.join(", ", missing.subList(0, limit));
        return missing.size() > limit ? joined + " …" : joined;
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
        return this.height / 2 - this.buildLines().size() * 6 - 104;
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

    private int row3Y() {
        return this.row2Y() + ROW_GAP;
    }

    private int chunkY() {
        return this.row3Y() + 20;
    }

    private int radiusY() {
        return this.chunkY() + 18;
    }

    private int safetyY() {
        return this.radiusY() + 24;
    }

    private int deleteY() {
        return this.safetyY() + 18;
    }

    private int buttonY() {
        return (this.showDeleteNewFiles() ? this.deleteY() : this.safetyY()) + 24;
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
