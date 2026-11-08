package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class GuiSnapshotList extends GuiBase {
    private static final int LIST_TOP = 40;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int ROW_HEIGHT = 34;
    private static final int RESTORE_BUTTON_WIDTH = 60;
    private static final int RESTORE_BUTTON_HEIGHT = 18;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private List<Commit> commits = Collections.emptyList();
    private boolean worldAvailable;
    private String message;
    private int scrollOffset;

    public GuiSnapshotList() {
        this.setTitle("SaveGit 快照列表");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.reload();

        int y = this.height - 28;
        int x = 20;
        this.addButton(new ButtonGeneric(x, y, 60, 20, "刷新"), (button, mouseButton) -> this.reload());
        x += 64;
        this.addButton(new ButtonGeneric(x, y, 72, 20, "立即快照"),
                (button, mouseButton) -> SaveGitSnapshotManager.manualSnapshot());
        x += 76;
        this.addButton(new ButtonGeneric(x, y, 60, 20, "设置"), (button, mouseButton) -> {
            GuiSaveGitConfigs gui = new GuiSaveGitConfigs();
            gui.setParent(this);
            GuiBase.openGui(gui);
        });

        int backWidth = 60;
        this.addButton(new ButtonGeneric(this.width - 20 - backWidth, y, backWidth, 20, "返回"),
                (button, mouseButton) -> this.close());
    }

    public static String formatTime(long timestamp) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    private void reload() {
        Path worldDir = SaveGitSnapshotManager.getWorldDirectory(this.mc);
        this.worldAvailable = worldDir != null;
        this.scrollOffset = 0;

        if (!this.worldAvailable) {
            this.commits = Collections.emptyList();
            this.message = "当前未加载单人世界";
            return;
        }

        this.commits = new SnapshotRepository(worldDir).listCommits();
        this.message = this.commits.isEmpty()
                ? "还没有任何快照。进入世界约 5 秒后会自动创建基线快照。"
                : null;
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        int listLeft = 20;
        int listWidth = this.width - 40;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        drawContext.fill(listLeft, LIST_TOP, listLeft + listWidth, listBottom, 0x50000000);

        if (!this.worldAvailable || this.commits.isEmpty()) {
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
            String line1 = "#" + commit.shortId() + "  " + formatTime(commit.timestamp)
                    + "  " + safe(commit.message);
            line1 = this.textRenderer.trimToWidth(line1, listWidth - RESTORE_BUTTON_WIDTH - 40);
            String line2 = "父提交: " + parent;

            this.drawString(drawContext, line1, listLeft + 8, rowY + 6, 0xFFFFFFFF);
            this.drawString(drawContext, line2, listLeft + 8, rowY + 18, 0xFFA0A0A0);

            int buttonX = listLeft + listWidth - RESTORE_BUTTON_WIDTH - 8;
            int buttonY = rowY + (ROW_HEIGHT - RESTORE_BUTTON_HEIGHT) / 2;
            boolean buttonHovered = mouseX >= buttonX && mouseX < buttonX + RESTORE_BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + RESTORE_BUTTON_HEIGHT;

            drawContext.fill(buttonX, buttonY,
                    buttonX + RESTORE_BUTTON_WIDTH, buttonY + RESTORE_BUTTON_HEIGHT,
                    buttonHovered ? 0xFF3A6EA5 : 0xFF2A4E75);
            String label = "恢复";
            this.drawString(drawContext, label,
                    buttonX + RESTORE_BUTTON_WIDTH / 2 - this.getStringWidth(label) / 2,
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
        if (mouseButton != 0 || this.commits.isEmpty() || !this.worldAvailable) {
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
            int buttonX = listLeft + listWidth - RESTORE_BUTTON_WIDTH - 8;
            int buttonY = rowY + (ROW_HEIGHT - RESTORE_BUTTON_HEIGHT) / 2;

            if (mouseX >= buttonX && mouseX < buttonX + RESTORE_BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + RESTORE_BUTTON_HEIGHT) {
                GuiBase.openGui(new GuiConfirmRestore(this.commits.get(i), this));
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
}
