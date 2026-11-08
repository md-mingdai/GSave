package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.RestoreProgress;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.snapshot.WorldRestorer;
import com.mingdai.savegit.snapshot.WorldSnapshotter;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 离线存档管理界面：在单人游戏列表点击「SaveGit 管理」后打开。
 * 支持恢复和删除快照（含 rebase）。
 */
public class GuiSaveGitWorldManager extends GuiBase {
    private static final int LIST_TOP = 40;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int ROW_HEIGHT = 34;
    private static final int RESTORE_BUTTON_WIDTH = 60;
    private static final int DELETE_BUTTON_WIDTH = 48;
    private static final int BUTTON_GAP = 6;
    private static final int ROW_BUTTON_HEIGHT = 18;

    private final Path worldDir;
    private final String worldDisplayName;
    private List<Commit> commits = Collections.emptyList();
    private String message;
    private int scrollOffset;

    public GuiSaveGitWorldManager(Path worldDir, String worldDisplayName) {
        this.worldDir = worldDir;
        this.worldDisplayName = worldDisplayName;
        this.setTitle("SaveGit 存档管理 - " + worldDisplayName);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int y = this.height - 28;
        this.addButton(new ButtonGeneric(20, y, 60, 20, "刷新"),
                (button, mouseButton) -> this.reload());

        int backWidth = 100;
        this.addButton(new ButtonGeneric(this.width - 20 - backWidth, y, backWidth, 20, "返回"),
                (button, mouseButton) -> this.close());
    }

    public static void open(MinecraftClient client, Path worldDir, String worldName) {
        client.setScreen(new GuiSaveGitWorldManager(worldDir, worldName));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void reload() {
        this.scrollOffset = 0;
        if (!Files.isDirectory(worldDir)) {
            this.commits = Collections.emptyList();
            this.message = "世界存档目录不存在";
            return;
        }

        Path saveGitDir = worldDir.resolve("savegit");
        if (!Files.isDirectory(saveGitDir)) {
            this.commits = Collections.emptyList();
            this.message = "该世界还没有任何 SaveGit 数据。";
            return;
        }

        this.commits = new SnapshotRepository(worldDir).listCommits();
        this.message = this.commits.isEmpty()
                ? "该世界还没有任何快照。"
                : null;
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        int listLeft = 20;
        int listWidth = this.width - 40;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        drawContext.fill(listLeft, LIST_TOP, listLeft + listWidth, listBottom, 0x50000000);

        if (this.commits.isEmpty()) {
            String text = this.message == null ? "还没有任何快照" : this.message;
            this.drawCentered(drawContext, text, (LIST_TOP + listBottom) / 2 - 4, 0xFFB0B0B0);
            return;
        }

        this.drawString(drawContext, "共 " + this.commits.size() + " 个快照",
                listLeft, LIST_TOP - 13, 0xFFCCCCCC);

        int visibleHeight = listBottom - LIST_TOP;
        int contentHeight = this.commits.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        this.scrollOffset = clamp(this.scrollOffset, 0, maxScroll);

        drawContext.enableScissor(listLeft, LIST_TOP, listLeft + listWidth, listBottom);

        int first = this.scrollOffset / ROW_HEIGHT;
        int last = Math.min(this.commits.size(), (this.scrollOffset + visibleHeight) / ROW_HEIGHT + 1);
        boolean deleteButtonHovered = false;

        for (int i = first; i < last; i++) {
            Commit commit = this.commits.get(i);
            int rowY = LIST_TOP + i * ROW_HEIGHT - this.scrollOffset;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (hovered) {
                drawContext.fill(listLeft, rowY, listLeft + listWidth, rowY + ROW_HEIGHT, 0x30FFFFFF);
            } else if (i % 2 == 1) {
                drawContext.fill(listLeft, rowY, listLeft + listWidth, rowY + ROW_HEIGHT, 0x18FFFFFF);
            }
            drawContext.fill(listLeft, rowY + ROW_HEIGHT - 1,
                    listLeft + listWidth, rowY + ROW_HEIGHT, 0x50FFFFFF);

            String parent = commit.parent == null ? "无（首个快照）" : "#" + shortId(commit.parent);
            String line1 = "#" + commit.shortId() + "  " + GuiSnapshotList.formatTime(commit.timestamp)
                    + "  " + safe(commit.message);
            line1 = this.textRenderer.trimToWidth(line1,
                    listWidth - RESTORE_BUTTON_WIDTH - DELETE_BUTTON_WIDTH - BUTTON_GAP - 40);
            String line2 = "父提交: " + parent;

            this.drawString(drawContext, line1, listLeft + 8, rowY + 6, 0xFFFFFFFF);
            this.drawString(drawContext, line2, listLeft + 8, rowY + 18, 0xFFA0A0A0);

            int buttonY = rowY + (ROW_HEIGHT - ROW_BUTTON_HEIGHT) / 2;

            int restoreX = listLeft + listWidth - RESTORE_BUTTON_WIDTH - 8;
            boolean restoreHovered = mouseX >= restoreX && mouseX < restoreX + RESTORE_BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + ROW_BUTTON_HEIGHT;
            drawContext.fill(restoreX, buttonY,
                    restoreX + RESTORE_BUTTON_WIDTH, buttonY + ROW_BUTTON_HEIGHT,
                    restoreHovered ? 0xFF3A6EA5 : 0xFF2A4E75);
            String restoreLabel = "恢复";
            this.drawString(drawContext, restoreLabel,
                    restoreX + RESTORE_BUTTON_WIDTH / 2 - this.getStringWidth(restoreLabel) / 2,
                    buttonY + 5, 0xFFFFFFFF);

            int deleteX = restoreX - DELETE_BUTTON_WIDTH - BUTTON_GAP;
            boolean deleteHovered = mouseX >= deleteX && mouseX < deleteX + DELETE_BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + ROW_BUTTON_HEIGHT;
            drawContext.fill(deleteX, buttonY,
                    deleteX + DELETE_BUTTON_WIDTH, buttonY + ROW_BUTTON_HEIGHT,
                    deleteHovered ? 0xFFAA3333 : 0xFF5A2A2A);
            String deleteLabel = "删除";
            this.drawString(drawContext, deleteLabel,
                    deleteX + DELETE_BUTTON_WIDTH / 2 - this.getStringWidth(deleteLabel) / 2,
                    buttonY + 5, 0xFFFFFFFF);
        }

        drawContext.disableScissor();

        if (maxScroll > 0) {
            int barX = listLeft + listWidth - 4;
            int barHeight = Math.max(20, visibleHeight * visibleHeight / contentHeight);
            int barY = LIST_TOP + (visibleHeight - barHeight) * this.scrollOffset / maxScroll;
            drawContext.fill(barX, LIST_TOP, barX + 4, listBottom, 0x40000000);
            drawContext.fill(barX, barY, barX + 4, barY + barHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0 || this.commits.isEmpty()) {
            return false;
        }

        int listLeft = 20;
        int listWidth = this.width - 40;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;
        if (mouseY < LIST_TOP || mouseY >= listBottom) {
            return false;
        }

        for (int i = 0; i < this.commits.size(); i++) {
            int rowY = LIST_TOP + i * ROW_HEIGHT - this.scrollOffset;
            if (rowY + ROW_HEIGHT <= LIST_TOP || rowY >= listBottom) {
                continue;
            }
            int restoreX = listLeft + listWidth - RESTORE_BUTTON_WIDTH - 8;
            int deleteX = restoreX - DELETE_BUTTON_WIDTH - BUTTON_GAP;
            int buttonY = rowY + (ROW_HEIGHT - ROW_BUTTON_HEIGHT) / 2;

            if (mouseX >= deleteX && mouseX < deleteX + DELETE_BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + ROW_BUTTON_HEIGHT) {
                // 删除按钮：弹出确认对话框
                GuiBase.openGui(new GuiExternalDeleteConfirm(
                        this.commits.get(i), this, this.worldDir));
                return true;
            }
        }
        return false;
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
        if (mouseX < 20 || mouseX > this.width - 20 || mouseY < LIST_TOP || mouseY > listBottom) {
            return false;
        }

        int visibleHeight = listBottom - LIST_TOP;
        int maxScroll = Math.max(0, this.commits.size() * ROW_HEIGHT - visibleHeight);
        this.scrollOffset = clamp(this.scrollOffset - (int) verticalAmount * ROW_HEIGHT, 0, maxScroll);
        return true;
    }

    private void drawCentered(DrawContext drawContext, String text, int y, int color) {
        this.drawString(drawContext, text, this.width / 2 - this.getStringWidth(text) / 2, y, color);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    /**
     * 外部模式的恢复确认界面。
     */
    public static class GuiExternalRestoreConfirm extends GuiBase {
        private final Commit commit;
        private final GuiSaveGitWorldManager parent;
        private final Path worldDir;
        private boolean createSafetySnapshot = true;
        private com.mingdai.savegit.gui.WidgetCheckBoxSimple safetyCheckBox;

        public GuiExternalRestoreConfirm(Commit commit, GuiSaveGitWorldManager parent, Path worldDir) {
            this.commit = commit;
            this.parent = parent;
            this.worldDir = worldDir;
            this.setTitle("确认恢复快照");
        }

        @Override
        public void initGui() {
            super.initGui();

            this.safetyCheckBox = new com.mingdai.savegit.gui.WidgetCheckBoxSimple(
                    0, 0, "恢复前创建安全快照", true);
            this.safetyCheckBox.setPosition(
                    this.width / 2 - this.safetyCheckBox.getWidth() / 2, this.height / 2 + 10);
            this.addWidget(this.safetyCheckBox);

            int buttonWidth = 100;
            int y = this.height / 2 + 42;
            this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20, "确认恢复"),
                    (button, mouseButton) -> {
                        this.mc.setScreen(null);
                        startExternalRestore(this.mc, this.worldDir, this.commit,
                                this.safetyCheckBox.isChecked());
                    });
            this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20, "取消"),
                    (button, mouseButton) -> {
                        if (this.parent != null) {
                            GuiBase.openGui(this.parent);
                        } else {
                            this.close();
                        }
                    });
        }

        @Override
        protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
            super.drawContents(drawContext, mouseX, mouseY, partialTicks);

            List<String> lines = new ArrayList<>();
            lines.add("即将恢复到快照 #" + this.commit.shortId());
            lines.add("时间: " + com.mingdai.savegit.gui.GuiSnapshotList.formatTime(this.commit.timestamp));
            lines.add("消息: " + (this.commit.message == null ? "" : this.commit.message));
            lines.add("");
            lines.add("恢复前会自动创建一个安全快照，取消勾选将跳过此步骤。");
            lines.add("恢复过程中请勿关闭游戏。");

            int startY = this.height / 2 - lines.size() * 6 - 26;
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
    public static class GuiExternalDeleteConfirm extends GuiBase {
        private final Commit commit;
        private final GuiSaveGitWorldManager parent;
        private final Path worldDir;

        public GuiExternalDeleteConfirm(Commit commit, GuiSaveGitWorldManager parent, Path worldDir) {
            this.commit = commit;
            this.parent = parent;
            this.worldDir = worldDir;
            this.setTitle("确认删除快照");
        }

        @Override
        public void initGui() {
            super.initGui();

            int buttonWidth = 100;
            int y = this.height / 2 + 40;
            this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20, "确认删除"),
                    (button, mouseButton) -> {
                        this.mc.setScreen(null);
                        performDelete(this.mc, this.worldDir, this.commit.id);
                    });
            this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20, "取消"),
                    (button, mouseButton) -> {
                        if (this.parent != null) {
                            GuiBase.openGui(this.parent);
                        } else {
                            this.close();
                        }
                    });
        }

        @Override
        protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
            super.drawContents(drawContext, mouseX, mouseY, partialTicks);

            List<String> lines = new ArrayList<>();
            lines.add("即将删除快照 #" + this.commit.shortId());
            lines.add("时间: " + com.mingdai.savegit.gui.GuiSnapshotList.formatTime(this.commit.timestamp));
            lines.add("消息: " + (this.commit.message == null ? "" : this.commit.message));
            lines.add("");
            lines.add("删除后子提交将自动 rebase 到父提交，无法撤销。");

            int startY = this.height / 2 - lines.size() * 6 - 20;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int color = i == 0 ? 0xFFFF6060 : 0xFFB0B0B0;
                this.drawString(drawContext, line,
                        this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
            }
        }

        private static void performDelete(MinecraftClient mc, Path worldDir, String commitId) {
            mc.setScreen(null);
            SnapshotRepository repo = new SnapshotRepository(worldDir);
            if (repo.deleteCommit(commitId)) {
                InfoUtils.sendVanillaMessage(
                        Text.literal("快照已删除: " + commitId.substring(0, 8)));
            } else {
                InfoUtils.sendVanillaMessage(
                        Text.literal("删除失败：找不到该快照"));
            }
        }
    }

    private static void startExternalRestore(MinecraftClient mc, Path worldDir, Commit commit,
                                             boolean createSafetySnapshot) {
        if (!createSafetySnapshot) {
            beginExternalRestore(mc, worldDir, commit);
            return;
        }

        WorldSnapshotter.Settings safetySettings = new WorldSnapshotter.Settings(
                true, true, true, true, true, Integer.MAX_VALUE,
                worldDir.getFileName() == null ? "未知世界" : worldDir.getFileName().toString(),
                "1.21.8"
        );

        new Thread(() -> {
            try {
                SnapshotRepository repo = new SnapshotRepository(worldDir);
                new WorldSnapshotter(repo).takeSnapshot("恢复前自动快照", safetySettings);
            } catch (Throwable t) {
                mc.execute(() -> InfoUtils.sendVanillaMessage(
                        Text.literal("恢复已取消：安全快照创建失败（" + t.getMessage() + "）")));
                return;
            }
            mc.execute(() -> beginExternalRestore(mc, worldDir, commit));
        }, "SaveGit-SafetySnapshot").start();
    }

    private static void beginExternalRestore(MinecraftClient mc, Path worldDir, Commit commit) {
        com.mingdai.savegit.snapshot.RestoreProgress.begin(0);
        mc.setScreen(new GuiRestoreProgress());

        new Thread(() -> {
            try {
                SnapshotRepository repo = new SnapshotRepository(worldDir);
                Commit c = repo.getCommit(commit.id);
                if (c == null) {
                    throw new IOException("找不到快照提交: " + commit.id);
                }
                new WorldRestorer(repo, worldDir).restore(c);
            } catch (Throwable t) {
                com.mingdai.savegit.snapshot.RestoreProgress.fail(t.getMessage());
            }
        }, "SaveGit-ExternalRestore").start();
    }
}
