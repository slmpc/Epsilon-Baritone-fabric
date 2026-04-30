package com.github.slmpc.epsilon_baritone;

import com.github.epsilon.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.fabric.addon.FabricEpsilonAddonEntrypoint;

public class EpsilonBaritoneEntrypoint implements FabricEpsilonAddonEntrypoint {

    @Override
    public void registerAddon(EpsilonAddonSetupEvent event) {
        event.registerAddon(EpsilonBaritone.INSTANCE);
    }

}
