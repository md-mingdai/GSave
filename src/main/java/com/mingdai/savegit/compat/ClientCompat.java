package com.mingdai.savegit.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

import java.util.List;

//? if >=1.21.6 {
import net.minecraft.client.gl.RenderPipelines;
//?} else if >=1.21.2 {
/*import net.minecraft.client.render.RenderLayer;
*///?}

import java.util.function.Consumer;

/**
 * 跨 Minecraft 版本差异的客户端 API 适配层。
 * 具体分支由 Stonecutter 在构建时按版本启用。
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** 绘制一张 GUI 贴图（1.21.1 / 1.21.2+ / 1.21.6+ 三种 API）。 */
    public static void drawTexture(DrawContext context, Identifier texture,
                                   int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                x, y, u, v, width, height, textureWidth, textureHeight);
        //?} else if >=1.21.2 {
        /*context.drawTexture(RenderLayer::getGuiTextured, texture,
                x, y, u, v, width, height, textureWidth, textureHeight);
        *///?} else {
        /*context.drawTexture(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        *///?}
    }

    /** 由 NativeImage 创建可用纹理（1.21.5 起需要名称提供器）。 */
    public static NativeImageBackedTexture createTexture(NativeImage image) {
        //? if >=1.21.5 {
        return new NativeImageBackedTexture(() -> "gsave_shot", image);
        //?} else {
        /*return new NativeImageBackedTexture(image);
        *///?}
    }

    /** 截取当前画面（1.21.5 起为异步回调，之前为同步返回）。 */
    public static void takeScreenshot(Framebuffer framebuffer, Consumer<NativeImage> consumer) {
        //? if >=1.21.5 {
        ScreenshotRecorder.takeScreenshot(framebuffer, consumer);
        //?} else {
        /*consumer.accept(ScreenshotRecorder.takeScreenshot(framebuffer));
        *///?}
    }

    /** 断开世界（1.21.6 起才有带保存界面的版本）。 */
    public static void disconnectWithSaving(MinecraftClient client) {
        //? if >=1.21.6 {
        client.disconnectWithSavingScreen();
        //?} else {
        /*client.disconnect();
        *///?}
    }

    /** 读取像素颜色（1.21.2 起为 ...Argb 命名）。 */
    public static int getPixel(NativeImage image, int x, int y) {
        //? if >=1.21.2 {
        return image.getColorArgb(x, y);
        //?} else {
        /*return image.getColor(x, y);
        *///?}
    }

    public static void setPixel(NativeImage image, int x, int y, int color) {
        //? if >=1.21.2 {
        image.setColorArgb(x, y, color);
        //?} else {
        /*image.setColor(x, y, color);
        *///?}
    }

    /** 绘制多行悬浮提示（MaLiLib 旧版本参数顺序不同）。 */
    public static void drawHoverText(DrawContext context, int mouseX, int mouseY,
                                     List<String> lines) {
        //? if >=1.21.6 {
        RenderUtils.drawHoverText(context, mouseX, mouseY, lines);
        //?} else {
        /*RenderUtils.drawHoverText(mouseX, mouseY, lines, context);
        *///?}
    }
}
