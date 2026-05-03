package com.github.slmpc.epsilon_baritone.mixins;

import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityEyeHeightMixin {

    @Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
    private void onGetDefaultEyeHeight(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player && AutoElytraService.INSTANCE.shouldFixEyeHeight()) {
            cir.setReturnValue(0.6F);
        }
    }
}

