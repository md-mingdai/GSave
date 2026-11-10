package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.RestoreProgress;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.snapshot.WorldRestorer;
import com.mingdai.savegit.snapshot.WorldSnapshotter;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 离线存档管理界面：在单人游戏列表点击「GSave 管理」后打开。
 * 支持恢复和删除快照（含 rebase）。
 */
public class GuiSaveGitWorldManager extends GuiSaveGitScreen {
    private static final int LIST_TOP = 44;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int CARD_GAP = 4;
    private static final int ROW_STRIDE = SnapshotCardRenderer.CARD_HEIGHT + CARD_GAP;
    private static final int LIST_SIDE_MARGIN = 20;

    private final Path worldDir;
    private final String worldDisplayName;
    private final Screen returnScreen;
    private List<Commit> commits = Collections.emptyList();
    private Set<String> screenshotIds = Collections.emptySet();
    private String message;
    private int scrollOffset;
    private final SnapshotThumbnailCache thumbnails = new SnapshotThumbnailCache();

    public GuiSaveGitWorldManager(Path worldDir, String worldDisplayName) {
        this(worldDir, worldDisplayName, null);
    }

    public GuiSaveGitWorldManager(Path worldDir, String worldDisplayName, Screen returnScreen) {
        this.worldDir = worldDir;
        this.worldDisplayName = worldDisplayName;
        this.returnScreen = returnScreen;
        this.setParent(returnScreen);
        this.setTitle(L10n.tr("gsave.gui.title.world_manager", worldDisplayName));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int y = this.height - 28;
        this.addButton(new ButtonGeneric(20, y, 60, 20, L10n.tr("gsave.button.refresh")),
                (button, mouseButton) -> this.reload());
        this.addButton(new ButtonGeneric(88, y, 110, 20, L10n.tr("gsave.button.maintenance")),
                (button, mouseButton) -> GuiBase.openGui(
                        new GuiRepositoryMaintenance(this.worldDir,
                                this.worldDisplayName, this)));

        int backWidth = 100;
        this.addButton(new ButtonGeneric(this.width - 20 - backWidth, y, backWidth, 20,
                        L10n.tr("gsave.button.back")),
                (button, mouseButton) -> this.goBack());
    }

    private void goBack() {
        if (this.returnScreen != null) {
            this.mc.setScreen(this.returnScreen);
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

    public static void open(MinecraftClient client, Path worldDir, String worldName) {
        client.setScreen(new GuiSaveGitWorldManager(worldDir, worldName, client.currentScreen));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void reload() {
        this.scrollOffset = 0;
        if (!Files.isDirectory(worldDir)) {
            this.commits = Collections.emptyList();
            this.screenshotIds = Collections.emptySet();
            this.message = L10n.tr("gsave.gui.message.world_dir_missing");
            return;
        }

        Path saveGitDir = SnapshotRepository.dataDir(worldDir);
        if (!Files.isDirectory(saveGitDir)) {
            this.commits = Collections.emptyList();
            this.screenshotIds = Collections.emptySet();
            this.message = L10n.tr("gsave.gui.message.no_data");
            return;
        }

        this.commits = new SnapshotRepository(worldDir).listCommits();
        this.message = this.commits.isEmpty()
                ? L10n.tr("gsave.gui.message.no_snapshots_world")
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
    public void removed() {
        super.removed();
        this.thumbnails.close();
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        int listLeft = LIST_SIDE_MARGIN;
        int listWidth = this.width - LIST_SIDE_MARGIN * 2;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        if (this.commits.isEmpty()) {
            String text = this.message == null
                    ? L10n.tr("gsave.gui.message.no_snapshots") : this.message;
            GuiTheme.drawEmptyState(drawContext, this.textRenderer, this.width / 2,
                    (LIST_TOP + listBottom) / 2 - 4, text, null);
            return;
        }

        this.drawString(drawContext,
                L10n.tr("gsave.gui.value.total_snapshots", String.valueOf(this.commits.size())),
                listLeft, LIST_TOP - 14, GuiTheme.TEXT_DIM);

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
            boolean hasShot = this.screenshotIds.contains(commit.id);

            SnapshotThumbnailCache.Entry thumbnail = null;
            if (hasShot) {
                Path shotFile = SnapshotRepository.dataDir(worldDir).resolve("shots")
                        .resolve(commit.id + ".png");
                thumbnail = this.thumbnails.getThumbnail(shotFile, commit.id);
            }

            SnapshotCardRenderer.draw(drawContext, this.textRenderer, layout, commit,
                    thumbnail, hasShot, commit.pinned, true, hovered, false, mouseX, mouseY);
        }

        drawContext.disableScissor();

        GuiTheme.drawScrollbar(drawContext, listLeft + listWidth - 4, LIST_TOP, listBottom,
                this.scrollOffset, maxScroll);
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0 || this.commits.isEmpty()) {
            return false;
        }

        int listLeft = LIST_SIDE_MARGIN;
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
            Commit rowCommit = this.commits.get(i);
            SnapshotCardRenderer.Layout layout =
                    SnapshotCardRenderer.layout(listLeft, listWidth, rowY);
            boolean hasShot = this.screenshotIds.contains(rowCommit.id);
            SnapshotCardRenderer.Action action =
                    SnapshotCardRenderer.hitTest(layout, mouseX, mouseY, hasShot, true);

            switch (action) {
                case THUMBNAIL -> {
                    Path shotFile = SnapshotRepository.dataDir(this.worldDir).resolve("shots")
                            .resolve(rowCommit.id + ".png");
                    GuiBase.openGui(new GuiScreenshotViewer(shotFile, rowCommit.id, this));
                    return true;
                }
                case RESTORE -> {
                    GuiBase.openGui(new GuiExternalRestoreConfirm(rowCommit, this, this.worldDir));
                    return true;
                }
                case DELETE -> {
                    GuiBase.openGui(new GuiExternalDeleteConfirm(rowCommit, this, this.worldDir));
                    return true;
                }
                case PIN -> {
                    this.togglePin(rowCommit);
                    return true;
                }
                case EDIT_NOTE -> {
                    GuiBase.openGui(new GuiEditNote(rowCommit, this.worldDir, this));
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private void togglePin(Commit commit) {
        try {
            new SnapshotRepository(this.worldDir).setPinned(commit.id, !commit.pinned);
            this.reload();
        } catch (IOException e) {
            this.message = L10n.tr("gsave.message.pin_failed", e.getMessage());
        }
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
        if (mouseX < LIST_SIDE_MARGIN || mouseX > this.width - LIST_SIDE_MARGIN
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

    /**
     * 外部模式的恢复确认界面。
     */
    public static class GuiExternalRestoreConfirm extends GuiSaveGitScreen {
        private final Commit commit;
        private final GuiSaveGitWorldManager parent;
        private final Path worldDir;
        private boolean createSafetySnapshot = true;
        private com.mingdai.savegit.gui.WidgetCheckBoxSimple safetyCheckBox;
        private com.mingdai.savegit.gui.WidgetCheckBoxSimple regionBox;
        private com.mingdai.savegit.gui.WidgetCheckBoxSimple entitiesBox;
        private com.mingdai.savegit.gui.WidgetCheckBoxSimple poiBox;
        private com.mingdai.savegit.gui.WidgetCheckBoxSimple otherBox;

        public GuiExternalRestoreConfirm(Commit commit, GuiSaveGitWorldManager parent, Path worldDir) {
            this.commit = commit;
            this.parent = parent;
            this.worldDir = worldDir;
            this.setTitle(L10n.tr("gsave.gui.title.confirm_restore"));
        }

        @Override
        public void initGui() {
            super.initGui();

            this.safetyCheckBox = new com.mingdai.savegit.gui.WidgetCheckBoxSimple(
                    0, 0, L10n.tr("gsave.gui.checkbox.safety_snapshot"), true);
            this.safetyCheckBox.setPosition(
                    this.width / 2 - this.safetyCheckBox.getWidth() / 2, this.height / 2 - 6);
            this.addWidget(this.safetyCheckBox);

            this.regionBox = this.addScopeBox(L10n.tr("gsave.gui.checkbox.scope_region"),
                    this.width / 2 - 130, this.height / 2 + 12);
            this.entitiesBox = this.addScopeBox(L10n.tr("gsave.gui.checkbox.scope_entities"),
                    this.width / 2 - 4, this.height / 2 + 12);
            this.poiBox = this.addScopeBox(L10n.tr("gsave.gui.checkbox.scope_poi"),
                    this.width / 2 - 130, this.height / 2 + 28);
            this.otherBox = this.addScopeBox(L10n.tr("gsave.gui.checkbox.scope_other"),
                    this.width / 2 - 4, this.height / 2 + 28);

            int buttonWidth = 100;
            int y = this.height / 2 + 52;
            this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                            L10n.tr("gsave.button.confirm_restore")),
                    (button, mouseButton) -> {
                        this.mc.setScreen(null);
                        startExternalRestore(this.mc, this.worldDir, this.commit,
                                this.safetyCheckBox.isChecked(),
                                new com.mingdai.savegit.snapshot.WorldRestorer.RestoreFilter(
                                        this.regionBox.isChecked(),
                                        this.entitiesBox.isChecked(),
                                        this.poiBox.isChecked(),
                                        this.otherBox.isChecked()));
                    });
            this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                            L10n.tr("gsave.button.cancel")),
                    (button, mouseButton) -> {
                        if (this.parent != null) {
                            GuiBase.openGui(this.parent);
                        } else {
                            this.close();
                        }
                    });
        }

        @Override
        public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
            if (keyCode == KeyCodes.KEY_ESCAPE) {
                if (this.parent != null) {
                    GuiBase.openGui(this.parent);
                } else {
                    this.close();
                }
                return true;
            }
            return super.onKeyTyped(keyCode, scanCode, modifiers);
        }

        private com.mingdai.savegit.gui.WidgetCheckBoxSimple addScopeBox(String label,
                                                                        int x, int y) {
            com.mingdai.savegit.gui.WidgetCheckBoxSimple box =
                    new com.mingdai.savegit.gui.WidgetCheckBoxSimple(x, y, label, true);
            this.addWidget(box);
            return box;
        }

        @Override
        protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
            super.drawContents(drawContext, mouseX, mouseY, partialTicks);

            List<String> lines = new ArrayList<>();
            lines.add(L10n.tr("gsave.gui.message.restore_target", this.commit.shortId()));
            lines.add(L10n.tr("gsave.gui.label.time",
                    com.mingdai.savegit.util.SnapshotTime.full(this.commit.timestamp)));
            if (this.commit.durationMs >= 0) {
                lines.add(L10n.tr("gsave.gui.label.duration",
                        com.mingdai.savegit.util.SnapshotTime.duration(this.commit.durationMs)));
            }
            lines.add(L10n.tr("gsave.gui.label.message", L10n.commitMessage(this.commit.message)));
            if (this.commit.mcVersion != null && !this.commit.mcVersion.isEmpty()
                    && !this.commit.mcVersion.equals(com.mingdai.savegit.SaveGitMod.MC_VERSION)) {
                lines.add(L10n.tr("gsave.gui.message.version_mismatch",
                        this.commit.mcVersion, com.mingdai.savegit.SaveGitMod.MC_VERSION));
            }
            lines.add("");
            lines.add(L10n.tr("gsave.gui.message.safety_hint"));
            lines.add(L10n.tr("gsave.gui.message.restore_warning_external"));

            int startY = this.height / 2 - lines.size() * 6 - 26;
            com.mingdai.savegit.gui.theme.GuiTheme.drawDialogPanel(drawContext,
                    this.textRenderer, this.width / 2, startY, lines);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int color = i == 0 ? 0xFFFFFFFF : 0xFFB0B0B0;
                this.drawString(drawContext, line,
                        this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
            }
        }
    }

    /**
     * 外部模式的删除确认界面。
     */
    public static class GuiExternalDeleteConfirm extends GuiSaveGitScreen {
        private final Commit commit;
        private final GuiSaveGitWorldManager parent;
        private final Path worldDir;

        public GuiExternalDeleteConfirm(Commit commit, GuiSaveGitWorldManager parent, Path worldDir) {
            this.commit = commit;
            this.parent = parent;
            this.worldDir = worldDir;
            this.setTitle(L10n.tr("gsave.gui.title.confirm_delete"));
        }

        @Override
        public void initGui() {
            super.initGui();

            int buttonWidth = 100;
            int y = this.height / 2 + 40;
            this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                            L10n.tr("gsave.button.confirm_delete")),
                    (button, mouseButton) -> this.performDelete());
            this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                            L10n.tr("gsave.button.cancel")),
                    (button, mouseButton) -> {
                        if (this.parent != null) {
                            GuiBase.openGui(this.parent);
                        } else {
                            this.close();
                        }
                    });
        }

        @Override
        public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
            if (keyCode == KeyCodes.KEY_ESCAPE) {
                if (this.parent != null) {
                    GuiBase.openGui(this.parent);
                } else {
                    this.close();
                }
                return true;
            }
            return super.onKeyTyped(keyCode, scanCode, modifiers);
        }

        @Override
        protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
            super.drawContents(drawContext, mouseX, mouseY, partialTicks);

            List<String> lines = new ArrayList<>();
            lines.add(L10n.tr("gsave.gui.message.delete_target", this.commit.shortId()));
            lines.add(L10n.tr("gsave.gui.label.time",
                    com.mingdai.savegit.util.SnapshotTime.full(this.commit.timestamp)));
            if (this.commit.durationMs >= 0) {
                lines.add(L10n.tr("gsave.gui.label.duration",
                        com.mingdai.savegit.util.SnapshotTime.duration(this.commit.durationMs)));
            }
            lines.add(L10n.tr("gsave.gui.label.message", L10n.commitMessage(this.commit.message)));
            if (this.commit.mcVersion != null && !this.commit.mcVersion.isEmpty()
                    && !this.commit.mcVersion.equals(com.mingdai.savegit.SaveGitMod.MC_VERSION)) {
                lines.add(L10n.tr("gsave.gui.message.version_mismatch",
                        this.commit.mcVersion, com.mingdai.savegit.SaveGitMod.MC_VERSION));
            }
            lines.add("");
            lines.add(L10n.tr("gsave.gui.message.delete_rebase"));

            int startY = this.height / 2 - lines.size() * 6 - 20;
            com.mingdai.savegit.gui.theme.GuiTheme.drawDialogPanel(drawContext,
                    this.textRenderer, this.width / 2, startY, lines);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int color = i == 0 ? 0xFFFF6060 : 0xFFB0B0B0;
                this.drawString(drawContext, line,
                        this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
            }
        }

        private void performDelete() {
            SnapshotRepository repo = new SnapshotRepository(this.worldDir);
            try {
                if (repo.deleteCommit(this.commit.id)) {
                    InfoUtils.sendVanillaMessage(L10n.text(
                            "gsave.message.snapshot_deleted", this.commit.shortId()));
                } else {
                    InfoUtils.sendVanillaMessage(
                            L10n.text("gsave.message.delete_failed_missing"));
                }
            } catch (IOException e) {
                InfoUtils.sendVanillaMessage(
                        L10n.text("gsave.message.delete_failed", e.getMessage()));
            }
            // 删除后刷新并回到存档管理列表，而不是退回主菜单
            GuiBase.openGui(new GuiSaveGitWorldManager(
                    this.worldDir, this.parent.worldDisplayName, this.parent.returnScreen));
        }
    }

    private static void startExternalRestore(MinecraftClient mc, Path worldDir, Commit commit,
                                             boolean createSafetySnapshot,
                                             WorldRestorer.RestoreFilter filter) {
        if (!createSafetySnapshot) {
            beginExternalRestore(mc, worldDir, commit, filter);
            return;
        }

        WorldSnapshotter.Settings safetySettings = new WorldSnapshotter.Settings(
                true, true, true, true, true, Integer.MAX_VALUE, false, false,
                worldDir.getFileName() == null
                        ? L10n.tr("gsave.gui.value.unknown_world")
                        : worldDir.getFileName().toString(),
                "1.21.8"
        );

        new Thread(() -> {
            try {
                SnapshotRepository repo = new SnapshotRepository(worldDir);
                new WorldSnapshotter(repo).takeSnapshot(L10n.REASON_SAFETY, safetySettings);
            } catch (Throwable t) {
                mc.execute(() -> InfoUtils.sendVanillaMessage(
                        L10n.text("gsave.message.safety_snapshot_failed", t.getMessage())));
                return;
            }
            mc.execute(() -> beginExternalRestore(mc, worldDir, commit, filter));
        }, "GSave-SafetySnapshot").start();
    }

    private static void beginExternalRestore(MinecraftClient mc, Path worldDir, Commit commit,
                                             WorldRestorer.RestoreFilter filter) {
        com.mingdai.savegit.snapshot.RestoreProgress.begin(0);
        mc.setScreen(new GuiRestoreProgress());

        new Thread(() -> {
            try {
                SnapshotRepository repo = new SnapshotRepository(worldDir);
                Commit c = repo.getCommit(commit.id);
                if (c == null) {
                    throw new IOException(L10n.tr("gsave.message.commit_missing", commit.id));
                }
                new WorldRestorer(repo, worldDir).restore(c, filter);
            } catch (Throwable t) {
                com.mingdai.savegit.snapshot.RestoreProgress.fail(t.getMessage());
            }
        }, "GSave-ExternalRestore").start();
    }
}
