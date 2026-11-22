package com.mingdai.savegit.mixin;

import com.mingdai.savegit.preview.PreviewManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 预览期间把鼠标视角增量从玩家转移到预览相机。
 */
@Mixin(Entity.class)
public abstract class EntityPreviewMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void gsave$redirectPreviewLook(double yawChange, double pitchChange, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        PreviewManager manager = PreviewManager.getInstance();
        if (((Object) this == mc.player || manager.isPreviewCamera(this))
                && manager.redirectLook(yawChange, pitchChange)) {
            ci.cancel();
        }
    }
}
