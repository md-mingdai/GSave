package com.mingdai.savegit.mixin;

import com.mingdai.savegit.preview.PreviewManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 预览期间不渲染第一人称手部。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPreviewMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void gsave$previewHideHand(CallbackInfo ci) {
        if (PreviewManager.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
