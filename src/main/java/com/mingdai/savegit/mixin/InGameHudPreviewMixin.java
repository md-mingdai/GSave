package com.mingdai.savegit.mixin;

import com.mingdai.savegit.preview.PreviewHudRenderer;
import com.mingdai.savegit.preview.PreviewManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 预览总览信息绘制在 HUD 左下角;预览期间隐藏准星。
 */
@Mixin(InGameHud.class)
public abstract class InGameHudPreviewMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void gsave$previewHud(DrawContext drawContext, RenderTickCounter tickCounter,
                                  CallbackInfo ci) {
        PreviewHudRenderer.render(drawContext);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void gsave$previewHideCrosshair(DrawContext drawContext, RenderTickCounter tickCounter,
                                            CallbackInfo ci) {
        if (PreviewManager.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
