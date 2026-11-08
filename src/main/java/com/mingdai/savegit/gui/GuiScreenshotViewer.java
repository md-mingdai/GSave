package com.mingdai.savegit.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gl.RenderPipelines;

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
        this.setTitle("快照截图 #" + shortId(commitId));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.image = this.cache.getFull(this.file, this.commitId);

        int y = this.height - 40;
        this.addButton(new ButtonGeneric(this.width / 2 - 50, y, 100, 20, "返回"),
                (button, mouseButton) -> GuiBase.openGui(this.parent));
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        if (this.image == null) {
            this.drawString(drawContext, "找不到该快照的截图",
                    this.width / 2 - this.getStringWidth("找不到该快照的截图") / 2,
                    this.height / 2 - 4, 0xFFFF8080);
            return;
        }

        int maxWidth = this.width - 80;
        int maxHeight = this.height - 110;
        float scale = Math.min(maxWidth / (float) this.image.width(),
                maxHeight / (float) this.image.height());
        scale = Math.min(scale, 4.0F);
        int drawWidth = Math.max(1, (int) (this.image.width() * scale));
        int drawHeight = Math.max(1, (int) (this.image.height() * scale));
        int x = (this.width - drawWidth) / 2;
        int y = Math.max(36, (this.height - drawHeight) / 2);

        drawContext.fill(x - 2, y - 2, x + drawWidth + 2, y + drawHeight + 2, 0xFF000000);
        drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, this.image.id(),
                x, y, 0.0F, 0.0F, drawWidth, drawHeight, this.image.width(), this.image.height());
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
