package com.github.slmpc.epsilon_baritone;

import com.github.epsilon.addon.EpsilonAddon;
import com.github.epsilon.events.bus.EventBus;
import com.github.slmpc.epsilon_baritone.elytra.AutoElytraService;
import com.github.slmpc.epsilon_baritone.modules.AutoElytra;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandles;

public class EpsilonBaritone extends EpsilonAddon {

    public static final EpsilonBaritone INSTANCE = new EpsilonBaritone();

    private final BaritoneAddonSettings addonSettings;

    private EpsilonBaritone() {
        super(EpsilonBaritoneMod.MOD_ID);
        this.addonSettings = new BaritoneAddonSettings(
                this::boolSetting,
                this::intSetting,
                this::doubleSetting,
                this::stringSetting
        );
    }

    @Override
    public String getDescription() {
        return "An addon that integrates Baritone with Epsilon.";
    }

    @Override
    public String getDisplayName() {
        return "Epsilon Baritone";
    }

    @Override
    public String getVersion() {
        return FabricLoader.getInstance()
                .getModContainer(EpsilonBaritoneMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void onSetup() {
        EventBus.INSTANCE.registerLambdaFactory(EpsilonBaritone.class.getPackageName(), (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        addonSettings.start();
        AutoElytraService.INSTANCE.initialize();

        registerModule(AutoElytra.INSTANCE);
    }
}
