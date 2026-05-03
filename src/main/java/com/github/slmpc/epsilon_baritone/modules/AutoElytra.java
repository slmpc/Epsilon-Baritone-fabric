package com.github.slmpc.epsilon_baritone.modules;

import baritone.api.BaritoneAPI;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.core.BlockPos;

public class AutoElytra extends Module {

    public enum Mode {
        ELYTRA,
        EXP_BOTTLE,
        INFINITY_ELYTRA
    }

    public enum FoodType {
        GOLDEN_CARROT,
        GOLDEN_APPLE,
        ENCHANTED_GOLDEN_APPLE,
        COOKED_BEEF,
        COOKED_PORKCHOP,
        COOKED_CHICKEN
    }

    public static final AutoElytra INSTANCE = new AutoElytra();

    private AutoElytra() {
        super("Auto Elytra", Category.PLAYER);
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.ELYTRA);

    private final BoolSetting setSeed = boolSetting("Set Seed", false);
    private final StringSetting seed = stringSetting("Seed", "", setSeed::getValue);

    private final IntSetting targetPosX = intSetting("Target Pos X", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
    private final IntSetting targetPosY = intSetting("Target Pos Y", 100, -64, 320, 1);
    private final IntSetting targetPosZ = intSetting("Target Pos Z", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);

    private final BoolSetting autoDisconnectOnFinish = boolSetting("Auto Disconnect Finish", false);
    private final BoolSetting autoDisconnectOnFailure = boolSetting("Auto Disconnect Failure", true);
    private final BoolSetting autoDisconnectOnFirstSegmentFailure = boolSetting("Auto Disconnect Seg1", false);
    private final BoolSetting lowHealthAutoDisconnect = boolSetting("Low Health Auto Disconnect", false);

    private final BoolSetting inspectArmor = boolSetting("Inspect Armor", true);
    private final BoolSetting verboseDebug = boolSetting("Verbose Debug", false);
    private final BoolSetting debugMessages = boolSetting("Debug Messages", false);

    private final EnumSetting<FoodType> foodType = enumSetting("Food", FoodType.GOLDEN_CARROT);

    private final BoolSetting hudEnabled = boolSetting("HUD", true);
    private final IntSetting hudX = intSetting("HUD X", 0, 0, 10000, 1, hudEnabled::getValue);
    private final IntSetting hudY = intSetting("HUD Y", 0, 0, 10000, 1, hudEnabled::getValue);

    private final BoolSetting renderTrajectory = boolSetting("Render Trajectory", true);
    private final BoolSetting hideRecoveryCamera = boolSetting("Hide Recovery Camera", true);

    @SuppressWarnings("unused")
    private final ButtonSetting goTrigger = buttonSetting("Go", this::goFunc);

    @SuppressWarnings("unused")
    private final ButtonSetting stopTrigger = buttonSetting("Stop", () -> AutoElytraService.INSTANCE.stop("Stopped by user."));

    private void goFunc() {
        if (AutoElytraService.INSTANCE.isRunning()) {
            ChatUtils.addChatMessage("Auto Elytra is already running.");
            return;
        }

        if (setSeed.getValue()) {
            try {
                BaritoneAPI.getSettings().elytraNetherSeed.value = Long.valueOf(seed.getValue());
            } catch (NumberFormatException e) {
                ChatUtils.addChatMessage("Invalid seed: " + seed.getValue());
                return;
            }
        }

        final var goal = new BlockPos(targetPosX.getValue(), targetPosY.getValue(), targetPosZ.getValue());
        AutoElytraService.INSTANCE.start(this, goal);
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public boolean shouldSetSeed() {
        return setSeed.getValue();
    }

    public String getSeed() {
        return seed.getValue();
    }

    public boolean isAutoDisconnectOnFinish() {
        return autoDisconnectOnFinish.getValue();
    }

    public boolean isAutoDisconnectOnFailure() {
        return autoDisconnectOnFailure.getValue();
    }

    public boolean isAutoDisconnectOnFirstSegmentFailure() {
        return autoDisconnectOnFirstSegmentFailure.getValue();
    }

    public boolean isLowHealthAutoDisconnect() {
        return lowHealthAutoDisconnect.getValue();
    }

    public boolean isInspectArmor() {
        return inspectArmor.getValue();
    }

    public boolean isVerboseDebug() {
        return verboseDebug.getValue();
    }

    public boolean isDebugMessages() {
        return debugMessages.getValue();
    }

    public FoodType getFoodType() {
        return foodType.getValue();
    }

    public boolean isHudEnabled() {
        return hudEnabled.getValue();
    }

    public int getHudX() {
        return hudX.getValue();
    }

    public int getHudY() {
        return hudY.getValue();
    }

    public boolean isRenderTrajectory() {
        return renderTrajectory.getValue();
    }

    public boolean isHideRecoveryCamera() {
        return hideRecoveryCamera.getValue();
    }

}
