package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuiSnapshotList extends GuiSaveGitScreen {
    private static final int LIST_TOP = 44;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = SnapshotCardRenderer.CARD_HEIGHT + CARD_GAP;
    private static final int LIST_LEFT = 20;
    private static final int LIST_SIDE_MARGIN = 20;

    private List<Commit> commits = Collections.emptyList();
    private Set<String> screenshotIds = Collections.emptySet();
    private Path worldDir;
    private final SnapshotThumbnailCache thumbnails = new SnapshotThumbnailCache();
    private boolean worldAvailable;
    private String message;
    private int scrollOffset;
    private boolean compareMode;
    private String compareBaseId;

    public GuiSnapshotList() {
        this.setTitle(L10n.tr("gsave.gui.title.snapshot_list"));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int y = this.height - 28;
        this.addButton(new ButtonGeneric(20, y, 60, 20, L10n.tr("gsave.button.refresh")),
                (button, mouseButton) -> this.reload());
        this.addButton(new ButtonGeneric(88, y, 60, 20, L10n.tr("gsave.button.compare")),
                (button, mouseButton) -> {
                    this.compareMode = !this.compareMode;
                    this.compareBaseId = null;
                    this.message = this.compareMode
                            ? L10n.tr("gsave.gui.message.compare_hint") : null;
                });
        int exportX = 156;
        int exportY = y;
        int backLeft = this.width - 20 - 100;
        if (exportX + 60 > backLeft - 8) {
            exportX = 20;
            exportY = y - 24;
        }
        this.addButton(new ButtonGeneric(exportX, exportY, 60, 20,
                        L10n.tr("gsave.button.export")),
                (button, mouseButton) -> this.exportSnapshots());

        int backWidth = 100;
        this.addButton(new ButtonGeneric(backLeft, y, backWidth, 20,
                        L10n.tr("gsave.button.back_to_main_menu")),
                (button, mouseButton) -> GuiBase.openGui(new GuiSaveGitMain()));
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        // 按 ESC 返回 GSave 主界面
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            GuiBase.openGui(new GuiSaveGitMain());
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        this.thumbnails.close();
    }

    private void reload() {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(this.mc);
        this.worldDir = worldDir;
        this.worldAvailable = worldDir != null;
        this.scrollOffset = 0;

        if (!this.worldAvailable) {
            this.commits = Collections.emptyList();
            this.screenshotIds = Collections.emptySet();
            this.message = L10n.tr("gsave.gui.message.world_unloaded");
            return;
        }

        this.commits = new SnapshotRepository(worldDir).listCommits();
        this.message = this.commits.isEmpty()
                ? L10n.tr("gsave.gui.message.no_snapshots_hint")
                : null;

        Set<String> shots = new HashSet<>();
        Path shotsDir = SnapshotRepository.dataDir(worldDir).resolve("shots");
        for (Commit commit : this.commits) {
            if (Files.isRegularFile(shotsDir.resolve(commit.id + ".png"))) {
                shots.add(commit.id);
            }
        }
        this.screenshotIds = shots;
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        int listLeft = LIST_LEFT;
        int listWidth = this.width - LIST_SIDE_MARGIN * 2;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        if (!this.worldAvailable || this.commits.isEmpty()) {
            String title = this.message == null
                    ? L10n.tr("gsave.gui.message.no_snapshots") : this.message;
            GuiTheme.drawEmptyState(drawContext, this.textRenderer, this.width / 2,
                    (LIST_TOP + listBottom) / 2 - 4, title,
                    this.worldAvailable ? L10n.tr("gsave.gui.empty.hint") : null);
            return;
        }

        String header = this.compareMode
                ? L10n.tr("gsave.gui.message.compare_hint")
                : L10n.tr("gsave.gui.value.total_snapshots",
                String.valueOf(this.commits.size()));
        this.drawString(drawContext, header, listLeft, LIST_TOP - 14,
                this.compareMode ? GuiTheme.ACCENT : GuiTheme.TEXT_DIM);

        int visibleHeight = listBottom - LIST_TOP;
        int contentHeight = this.commits.size() * ROW_STRIDE - CARD_GAP;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        this.scrollOffset = clamp(this.scrollOffset, 0, maxScroll);

        drawContext.enableScissor(listLeft, LIST_TOP, listLeft + listWidth, listBottom);

        int first = this.scrollOffset / ROW_STRIDE;
        int last = Math.min(this.commits.size(),
                (this.scrollOffset + visibleHeight) / ROW_STRIDE + 1);

        for (int i = first; i < last; i++) {
            Commit commit = this.commits.get(i);
            int rowY = LIST_TOP + i * ROW_STRIDE - this.scrollOffset;
            SnapshotCardRenderer.Layout layout =
                    SnapshotCardRenderer.layout(listLeft, listWidth, rowY);
            boolean hovered = SnapshotCardRenderer.isInsideCard(layout, mouseX, mouseY);
            boolean hasShot = this.screenshotIds.contains(commit.id) && this.worldDir != null;

            SnapshotThumbnailCache.Entry thumbnail = null;
            if (hasShot) {
                Path shotFile = SnapshotRepository.dataDir(this.worldDir).resolve("shots")
                        .resolve(commit.id + ".png");
                thumbnail = this.thumbnails.getThumbnail(shotFile, commit.id);
            }

            SnapshotCardRenderer.draw(drawContext, this.textRenderer, layout, commit,
                    thumbnail, hasShot, commit.pinned, false,
                    hovered, this.compareMode && commit.id.equals(this.compareBaseId),
                    mouseX, mouseY);
        }

        drawContext.disableScissor();

        GuiTheme.drawScrollbar(drawContext, listLeft + listWidth - 4, LIST_TOP, listBottom,
                this.scrollOffset, maxScroll);

        this.drawDeleteTooltip(drawContext, mouseX, mouseY, listLeft, listWidth, listBottom);
    }

    private void drawDeleteTooltip(DrawContext drawContext, int mouseX, int mouseY,
                                   int listLeft, int listWidth, int listBottom) {
        if (mouseY < LIST_TOP || mouseY >= listBottom) {
            return;
        }
        for (int i = 0; i < this.commits.size(); i++) {
            int rowY = LIST_TOP + i * ROW_STRIDE - this.scrollOffset;
            SnapshotCardRenderer.Layout layout =
                    SnapshotCardRenderer.layout(listLeft, listWidth, rowY);
            if (mouseX >= layout.deleteX()
                    && mouseX < layout.deleteX() + SnapshotCardRenderer.DELETE_WIDTH
                    && mouseY >= layout.buttonY()
                    && mouseY < layout.buttonY() + SnapshotCardRenderer.BUTTON_HEIGHT) {
                RenderUtils.drawHoverText(drawContext, mouseX, mouseY, List.of(
                        L10n.tr("gsave.gui.tooltip.delete_disabled"),
                        L10n.tr("gsave.gui.tooltip.delete_disabled_hint")));
                return;
            }
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0 || this.commits.isEmpty() || !this.worldAvailable) {
            return false;
        }

        int listLeft = LIST_LEFT;
        int listWidth = this.width - LIST_SIDE_MARGIN * 2;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;
        if (mouseY < LIST_TOP || mouseY >= listBottom) {
            return false;
        }

        for (int i = 0; i < this.commits.size(); i++) {
            int rowY = LIST_TOP + i * ROW_STRIDE - this.scrollOffset;
            if (rowY + SnapshotCardRenderer.CARD_HEIGHT <= LIST_TOP || rowY >= listBottom) {
                continue;
            }
            Commit commit = this.commits.get(i);
            SnapshotCardRenderer.Layout layout =
                    SnapshotCardRenderer.layout(listLeft, listWidth, rowY);
            if (this.compareMode && SnapshotCardRenderer.isInsideCard(layout, mouseX, mouseY)) {
                this.handleCompareClick(commit);
                return true;
            }
            boolean hasShot = this.screenshotIds.contains(commit.id);
            SnapshotCardRenderer.Action action =
                    SnapshotCardRenderer.hitTest(layout, mouseX, mouseY, hasShot, false);

            switch (action) {
                case THUMBNAIL -> {
                    Path shotFile = SnapshotRepository.dataDir(this.worldDir).resolve("shots")
                            .resolve(commit.id + ".png");
                    GuiBase.openGui(new GuiScreenshotViewer(shotFile, commit.id, this));
                    return true;
                }
                case RESTORE -> {
                    GuiBase.openGui(new GuiConfirmRestore(commit, this));
                    return true;
                }
                case PIN -> {
                    this.togglePin(commit);
                    return true;
                }
                case EDIT_NOTE -> {
                    GuiBase.openGui(new GuiEditNote(commit, this.worldDir, this));
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private void exportSnapshots() {
        if (!this.worldAvailable || this.worldDir == null) {
            this.message = L10n.tr("gsave.gui.message.world_unloaded");
            return;
        }
        String world = SaveGitSnapshotManager.getWorldName(this.mc)
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (world.isEmpty()) {
            world = "world";
        }
        Path target = this.mc.runDirectory.toPath().resolve("gsave-exports")
                .resolve("gsave-" + world + "-" + System.currentTimeMillis() + ".zip");
        Thread thread = new Thread(() -> {
            try {
                com.mingdai.savegit.snapshot.RepositoryArchive.ExportResult result =
                        com.mingdai.savegit.snapshot.RepositoryArchive.exportAll(
                                this.worldDir, target);
                InfoUtils.sendVanillaMessage(L10n.text("gsave.message.export_done",
                        String.valueOf(result.commits()), target.toString()));
            } catch (IOException e) {
                InfoUtils.sendVanillaMessage(
                        L10n.text("gsave.message.export_failed", e.getMessage()));
            }
        }, "GSave-Export");
        thread.setDaemon(true);
        thread.start();
        this.message = L10n.tr("gsave.message.export_started");
    }

    private void togglePin(Commit commit) {
        try {
            new SnapshotRepository(this.worldDir).setPinned(commit.id, !commit.pinned);
            this.reload();
        } catch (IOException e) {
            this.message = L10n.tr("gsave.message.pin_failed", e.getMessage());
        }
    }

    private void handleCompareClick(Commit commit) {
        if (this.compareBaseId == null) {
            this.compareBaseId = commit.id;
            return;
        }
        if (this.compareBaseId.equals(commit.id)) {
            this.compareBaseId = null;
            return;
        }
        Commit base = null;
        for (Commit candidate : this.commits) {
            if (candidate.id.equals(this.compareBaseId)) {
                base = candidate;
                break;
            }
        }
        if (base == null) {
            this.compareBaseId = commit.id;
            return;
        }

        SnapshotRepository repo = new SnapshotRepository(this.worldDir);
        com.mingdai.savegit.snapshot.Manifest from = repo.getManifest(base.manifestId);
        com.mingdai.savegit.snapshot.Manifest to = repo.getManifest(commit.manifestId);
        this.compareMode = false;
        this.compareBaseId = null;
        this.message = null;
        GuiBase.openGui(new GuiSnapshotDiff(
                com.mingdai.savegit.snapshot.SnapshotDiff.compute(
                        base.id, commit.id, from, to), this));
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (verticalAmount == 0 || this.commits.isEmpty()) {
            return false;
        }

        int listBottom = this.height - LIST_BOTTOM_MARGIN;
        if (mouseX < LIST_LEFT || mouseX > this.width - LIST_SIDE_MARGIN
                || mouseY < LIST_TOP || mouseY > listBottom) {
            return false;
        }

        int visibleHeight = listBottom - LIST_TOP;
        int contentHeight = this.commits.size() * ROW_STRIDE - CARD_GAP;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        this.scrollOffset = clamp(this.scrollOffset - (int) verticalAmount * ROW_STRIDE,
                0, maxScroll);
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String formatTime(long timestamp) {
        return com.mingdai.savegit.util.SnapshotTime.absolute(timestamp);
    }
}
