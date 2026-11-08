package com.mingdai.savegit.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.gui.DrawContext;

/**
 * SaveGit 界面基类：没有加载世界时使用原版滚动全景背景，
 * 在世界内则保留原版的世界背景。
 */
public abstract class GuiSaveGitScreen extends GuiBase {
    @Override
    protected void drawScreenBackground(DrawContext drawContext, int mouseX, int mouseY) {
        if (this.mc.world == null) {
            this.renderPanoramaBackground(drawContext, 0.0F);
            drawContext.fill(0, 0, this.width, this.height, 0x60000000);
        } else {
            super.drawScreenBackground(drawContext, mouseX, mouseY);
        }
    }
}
