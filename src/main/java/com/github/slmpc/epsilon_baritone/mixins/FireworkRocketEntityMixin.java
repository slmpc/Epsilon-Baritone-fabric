package com.github.slmpc.epsilon_baritone.mixins;

import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (AutoElytraService.INSTANCE.isCameraOverride()) {
            ci.cancel();
        }
    }
}

