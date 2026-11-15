package com.mingdai.savegit.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * 跨 Minecraft 版本差异的客户端 API 适配层。
 * 具体分支由 Stonecutter 在构建时按版本启用。
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** 绘制一张 GUI 贴图（1.21.1 / 1.21.2+ / 1.21.6+ 三种 API）。 */
    public static void drawTexture(GuiGraphicsExtractor graphics, Identifier texture,
                                   int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, u, v, width, height, textureWidth, textureHeight);
    }

    /** 由 NativeImage 创建可用纹理（1.21.5 起需要名称提供器）。 */
    public static DynamicTexture createTexture(NativeImage image) {
        return new DynamicTexture(() -> "gsave_shot", image);
    }

    /** 截取当前画面（1.21.5 起为异步回调，之前为同步返回）。 */
    public static void takeScreenshot(RenderTarget framebuffer, Consumer<NativeImage> consumer) {
        Screenshot.takeScreenshot(framebuffer, consumer);
    }

    /** 断开世界（1.21.6 起才有带保存界面的版本）。 */
    public static void disconnectWithSaving(Minecraft client) {
        client.disconnectWithSavingScreen();
    }

    /** 读取像素颜色（1.21.2 起为 ...Argb 命名）。 */
    public static int getPixel(NativeImage image, int x, int y) {
        return image.getPixel(x, y);
    }

    public static void setPixel(NativeImage image, int x, int y, int color) {
        image.setPixel(x, y, color);
    }

    /** 绘制多行悬浮提示（MaLiLib 旧版本参数顺序不同）。 */
    public static void drawHoverText(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     List<String> lines) {
        RenderUtils.drawHoverText(graphics, mouseX, mouseY, lines);
    }

    /** 26.1 中当前界面仍在 Minecraft 上,26.2 起移入 Gui。 */
    public static Screen currentScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
        *///?}
    }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else {
        /*client.setScreen(screen);
        *///?}
    }

    /** 26.2 起主渲染目标移到 GameRenderer。 */
    public static RenderTarget mainRenderTarget(Minecraft client) {
        //? if >=26.2 {
        return client.gameRenderer.mainRenderTarget();
        //?} else {
        /*return client.getMainRenderTarget();
        *///?}
    }
}
