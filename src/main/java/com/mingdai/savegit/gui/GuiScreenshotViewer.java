package com.mingdai.savegit.gui;

import com.mingdai.savegit.util.L10n;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gl.RenderPipelines;
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
        this.image = this.cache.getFull(this.file, this.commitId);

        int y = this.height - 40;
        this.addButton(new ButtonGeneric(this.width / 2 - 105, y, 100, 20,
                        L10n.tr("gsave.button.open_file")),
                (button, mouseButton) -> Util.getOperatingSystem().open(this.file));
        this.addButton(new ButtonGeneric(this.width / 2 + 5, y, 100, 20,
                        L10n.tr("gsave.button.back")),
                (button, mouseButton) -> GuiBase.openGui(this.parent));
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        if (this.image == null) {
            String message = L10n.tr("gsave.gui.message.screenshot_missing");
            this.drawString(drawContext, message,
                    this.width / 2 - this.getStringWidth(message) / 2,
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
                x, y, 0.0F, 0.0F, drawWidth, drawHeight,
                this.image.width(), this.image.height(),
                this.image.width(), this.image.height());
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        // 按 ESC 返回快照列表
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            GuiBase.openGui(this.parent);
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
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
