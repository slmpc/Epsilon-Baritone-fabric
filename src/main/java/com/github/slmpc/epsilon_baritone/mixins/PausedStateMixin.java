package com.github.slmpc.epsilon_baritone.mixins;

import baritone.Baritone;
import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(targets = "baritone.command.defaults.ExecutionControlCommands", remap = false)
public class PausedStateMixin {

    @Inject(method = "<init>", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void capturePauseArray(Baritone par1, CallbackInfo ci, @Local boolean[] paused) {
        AutoElytraService.INSTANCE.setPausedState(paused);
    }
}

