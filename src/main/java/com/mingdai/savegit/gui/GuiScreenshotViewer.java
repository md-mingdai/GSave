package com.mingdai.savegit.gui;

import com.mingdai.savegit.util.L10n;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import com.mingdai.savegit.compat.ClientCompat;
import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * 查看快照截图大图。
 */
public class GuiScreenshotViewer extends GuiSaveGitScreen {
    private final Path file;
    private final String commitId;
    private final Screen parent;
    private final SnapshotThumbnailCache cache = new SnapshotThumbnailCache();
    private SnapshotThumbnailCache.Entry image;

    public GuiScreenshotViewer(Path file, String commitId, Screen parent) {
        this.file = file;
        this.commitId = commitId;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.screenshot", shortId(commitId)));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.image = this.loadForSize();

        int y = this.height - 40;
        this.addButton(new ButtonGeneric(this.width / 2 - 105, y, 100, 20,
                        L10n.tr("gsave.button.open_file")),
                (button, mouseButton) -> Util.getOperatingSystem().open(this.file));
        this.addButton(new ButtonGeneric(this.width / 2 + 5, y, 100, 20,
                        L10n.tr("gsave.button.back")),
                (button, mouseButton) -> GuiBase.openGui(this.parent));
    }

    private int maxImageWidth() {
        return Math.max(1, this.width - 80);
    }

    private int maxImageHeight() {
        return Math.max(1, this.height - 110);
    }

    private SnapshotThumbnailCache.Entry loadForSize() {
        return this.cache.getScaled(this.file, this.commitId,
                this.maxImageWidth(), this.maxImageHeight());
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        // 窗口尺寸变化后按新边界重新等比缩放，保证完整显示
        if (this.image != null
                && (this.image.width() > this.maxImageWidth()
                || this.image.height() > this.maxImageHeight())) {
            this.image = this.loadForSize();
        }
        if (this.image == null) {
            this.image = this.loadForSize();
        }
        if (this.image == null) {
            String message = L10n.tr("gsave.gui.message.screenshot_missing");
            this.drawString(drawContext, message,
                    this.width / 2 - this.getStringWidth(message) / 2,
                    this.height / 2 - 4, 0xFFFF8080);
            return;
        }

        int drawWidth = this.image.width();
        int drawHeight = this.image.height();
        int x = (this.width - drawWidth) / 2;
        int y = Math.max(36, (this.height - drawHeight) / 2);

        drawContext.fill(x - 2, y - 2, x + drawWidth + 2, y + drawHeight + 2, 0xFF000000);
        ClientCompat.drawTexture(drawContext, this.image.id(),
                x, y, 0.0F, 0.0F, drawWidth, drawHeight,
                drawWidth, drawHeight);
    }

    @Override
    protected boolean onEscapePressed() {
        // 按 ESC 返回快照列表
        GuiBase.openGui(this.parent);
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        this.cache.close();
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
