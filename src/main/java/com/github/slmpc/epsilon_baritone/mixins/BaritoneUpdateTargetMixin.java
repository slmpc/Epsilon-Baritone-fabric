package com.github.slmpc.epsilon_baritone.mixins;

import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "baritone.behavior.LookBehavior", remap = false)
public class BaritoneUpdateTargetMixin {

    @Inject(method = "updateTarget", at = @At("HEAD"))
    private void onUpdateTarget(CallbackInfo ci) {
        AutoElytraService.INSTANCE.markBaritoneLookControlled();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        AutoElytraService.INSTANCE.setLookMixinReady();
    }
}

