package com.mingdai.savegit.mixin;

import com.mingdai.savegit.preview.DummyMovementInput;
import com.mingdai.savegit.preview.PreviewManager;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 预览期间阻止玩家移动,同时让玩家 tick 继续运行(相机已分离)。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPreviewMixin {
    @Shadow
    public ClientInput input;

    @Unique
    private final DummyMovementInput gsave$dummyInput = new DummyMovementInput(null);
    @Unique
    private ClientInput gsave$realInput;

    @Inject(method = "tick", at = @At("HEAD"))
    private void gsave$previewInputPre(CallbackInfo ci) {
        if (PreviewManager.getInstance().isActive()) {
            this.gsave$realInput = this.input;
            this.input = this.gsave$dummyInput;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void gsave$previewInputPost(CallbackInfo ci) {
        if (this.gsave$realInput != null) {
            if (this.input == this.gsave$dummyInput) {
                this.input = this.gsave$realInput;
            }
            this.gsave$realInput = null;
        }
    }

    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void gsave$previewIsCamera(CallbackInfoReturnable<Boolean> cir) {
        if (PreviewManager.getInstance().isActive()) {
            cir.setReturnValue(true);
        }
    }
}
