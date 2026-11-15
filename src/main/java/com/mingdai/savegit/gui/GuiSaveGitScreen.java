package com.mingdai.savegit.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.KeyCodes;

//? if >=1.21.9 {
import net.minecraft.client.input.KeyInput;
//?}

import net.minecraft.client.gui.DrawContext;

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
    public boolean shouldPause() {
        return true;
    }

    //? if >=1.21.9 {
    @Override
    public boolean onKeyTyped(KeyInput input) {
        if (input.key() == KeyCodes.KEY_ESCAPE && this.onEscapePressed()) {
            return true;
        }
        return super.onKeyTyped(input);
    }
    //?} else {
    /*@Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.KEY_ESCAPE && this.onEscapePressed()) {
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }
    *///?}

    /** 子界面按 ESC 时的行为,返回 true 表示已处理。 */
    protected boolean onEscapePressed() {
        return false;
    }

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
