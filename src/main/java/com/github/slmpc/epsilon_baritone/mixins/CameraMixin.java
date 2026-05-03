package com.github.slmpc.epsilon_baritone.mixins;

import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void onSetup(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (AutoElytraService.INSTANCE.isCameraOverride()) {
            setRotation(AutoElytraService.INSTANCE.getFixedYaw(), AutoElytraService.INSTANCE.getFixedPitch());
        }
    }
}

