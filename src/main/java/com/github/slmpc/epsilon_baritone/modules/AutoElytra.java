package com.github.slmpc.epsilon_baritone.modules;

import baritone.api.BaritoneAPI;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.core.BlockPos;

public class AutoElytra extends Module {

    public static final AutoElytra INSTANCE = new AutoElytra();

    private AutoElytra() {
        super("Auto Elytra", Category.WORLD);
    }

    private final BoolSetting setSeed = boolSetting("Set Seed", false);
    private final StringSetting seed = stringSetting("Seed", "", setSeed::getValue);

    private final IntSetting targetPosX = intSetting("Target Pos X", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    private final IntSetting targetPosY = intSetting("Target Pos Y", 100, -64, 320, 1);
    private final IntSetting targetPosZ = intSetting("Target Pos Z", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);

    @SuppressWarnings("unused")
    private final ButtonSetting goTrigger = buttonSetting("Go", this::goFunc);

    private void goFunc() {
        if (setSeed.getValue()) {
            try {
                BaritoneAPI.getSettings().elytraNetherSeed.value = Long.valueOf(seed.getValue());
            } catch (NumberFormatException e) {
                ChatUtils.addChatMessage("Invalid seed: " + seed.getValue());
            }
        }

        final var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        final var goal = new BlockPos(targetPosX.getValue(), targetPosY.getValue(), targetPosZ.getValue());
        baritone.getElytraProcess().pathTo(goal);
    }

}
