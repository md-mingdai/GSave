package com.mingdai.savegit.gui;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * SaveGit 界面基类：没有加载世界时使用原版滚动全景背景，
 * 在世界内则保留原版的世界背景。
 */
public abstract class GuiSaveGitScreen extends GuiBase {
    /**
     * MaLiLib 的 GuiBase 默认不暂停游戏，而 GSave 的界面都属于“暂停类”界面，
     * 在世界中打开时需要像原版暂停菜单一样暂停游戏。
     */
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean onKeyTyped(KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE && this.onEscapePressed()) {
            return true;
        }
        return super.onKeyTyped(input);
    }

    /** 子界面按 ESC 时的行为,返回 true 表示已处理。 */
    protected boolean onEscapePressed() {
        return false;
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        return this.handleMouseClicked((int) click.x(), (int) click.y(),
                click.button(), doubleClick)
                || super.onMouseClicked(click, doubleClick);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY,
                                   double horizontalAmount, double verticalAmount) {
        return this.handleMouseScrolled((int) mouseX, (int) mouseY,
                horizontalAmount, verticalAmount)
                || super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** 子界面自定义的鼠标点击处理（列表行、卡片等）。 */
    protected boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton,
                                         boolean doubleClick) {
        return false;
    }

    /** 子界面自定义的滚轮处理。 */
    protected boolean handleMouseScrolled(int mouseX, int mouseY,
                                          double horizontalAmount, double verticalAmount) {
        return false;
    }

    @Override
    protected void drawScreenBackground(GuiContext drawContext, int mouseX, int mouseY) {
        if (this.mc.level == null) {
            this.extractPanorama(drawContext, 0.0F);
            drawContext.fill(0, 0, this.width, this.height, 0x60000000);
        } else {
            super.drawScreenBackground(drawContext, mouseX, mouseY);
        }
    }
}
