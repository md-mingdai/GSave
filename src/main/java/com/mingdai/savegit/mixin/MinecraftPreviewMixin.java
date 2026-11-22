package com.mingdai.savegit.mixin;

import com.mingdai.savegit.preview.PreviewManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 预览期间拦截攻击/使用/挖掘,避免在自由视角下误操作玩家周围的世界。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPreviewMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void gsave$previewBlockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (PreviewManager.getInstance().isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void gsave$previewBlockUse(CallbackInfo ci) {
        if (PreviewManager.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void gsave$previewBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (PreviewManager.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
