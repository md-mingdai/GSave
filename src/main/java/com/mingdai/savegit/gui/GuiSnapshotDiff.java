package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.SnapshotDiff;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * 两个快照的差异总览。
 */
public class GuiSnapshotDiff extends GuiSaveGitScreen {
    private static final int MAX_ROWS = 26;

    private final SnapshotDiff.Diff diff;
    private final Screen parent;
    private int scrollOffset;

    public GuiSnapshotDiff(SnapshotDiff.Diff diff, Screen parent) {
        this.diff = diff;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.diff"));
    }

    @Override
    public void initGui() {
        super.initGui();
        int backWidth = 100;
        this.addButton(new ButtonGeneric(this.width - 20 - backWidth, this.height - 28,
                        backWidth, 20, L10n.tr("gsave.button.back")),
                (button, mouseButton) -> this.goBack());
    }

    private void goBack() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            this.close();
        }
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        int left = 20;
        int right = this.width - 20;
        int top = 44;
        int bottom = this.height - 40;
        GuiTheme.drawPanel(drawContext, left, top, right, bottom);

        int x = left + 10;
        int y = top + 10;

        this.drawString(drawContext, "#" + shortId(this.diff.fromId())
                + "  →  #" + shortId(this.diff.toId()), x, y, GuiTheme.ACCENT);
        y += 14;

        String summary = L10n.tr("gsave.diff.summary",
                String.valueOf(this.diff.addedFiles()),
                String.valueOf(this.diff.removedFiles()),
                String.valueOf(this.diff.modifiedFiles()),
                String.valueOf(this.diff.addedChunks()),
                String.valueOf(this.diff.removedChunks()),
                String.valueOf(this.diff.modifiedChunks()));
        this.drawString(drawContext, summary, x, y, GuiTheme.TEXT_MAIN);
        y += 14;

        GuiTheme.drawDivider(drawContext, x, y, right - 10, 0x40FFFFFF);
        y += 8;

        if (this.diff.files().isEmpty()) {
            this.drawString(drawContext, L10n.tr("gsave.diff.empty"),
                    x, y, GuiTheme.TEXT_DIM);
            return;
        }

        int visibleRows = Math.max(1, (bottom - y - 10) / 11);
        int maxScroll = Math.max(0, this.diff.files().size() - visibleRows);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));

        for (int i = this.scrollOffset;
             i < Math.min(this.diff.files().size(), this.scrollOffset + visibleRows); i++) {
            SnapshotDiff.FileDiff file = this.diff.files().get(i);
            String kind = switch (file.kind()) {
                case ADDED -> L10n.tr("gsave.diff.kind.added");
                case REMOVED -> L10n.tr("gsave.diff.kind.removed");
                case MODIFIED -> L10n.tr("gsave.diff.kind.modified");
            };
            int color = switch (file.kind()) {
                case ADDED -> GuiTheme.SUCCESS;
                case REMOVED -> GuiTheme.DANGER;
                case MODIFIED -> GuiTheme.WARNING;
            };
            String line = L10n.tr("gsave.diff.file", kind, file.path(),
                    String.valueOf(file.addedChunks()),
                    String.valueOf(file.removedChunks()),
                    String.valueOf(file.modifiedChunks()));
            line = this.textRenderer.trimToWidth(line, right - x - 20);
            this.drawString(drawContext, line, x, y, color);
            y += 11;
        }

        GuiTheme.drawScrollbar(drawContext, right - 10, top + 60, bottom - 4,
                this.scrollOffset, maxScroll);
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (verticalAmount != 0) {
            this.scrollOffset -= (int) verticalAmount;
            return true;
        }
        return false;
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "????????";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
