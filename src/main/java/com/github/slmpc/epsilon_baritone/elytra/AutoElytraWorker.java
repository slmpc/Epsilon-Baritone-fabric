package com.github.slmpc.epsilon_baritone.elytra;

import baritone.api.BaritoneAPI;
import com.github.slmpc.epsilon_baritone.modules.AutoElytra;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

public final class AutoElytraWorker extends Thread {

    private final AutoElytraService service;
    private final AutoElytra module;
    private final BlockPos target;
    private volatile boolean canceled;

    public AutoElytraWorker(@NotNull AutoElytraService service, @NotNull AutoElytra module, @NotNull BlockPos target) {
        super("AutoElytraWorker");
        this.service = service;
        this.module = module;
        this.target = target;
    }

    public void cancel() {
        this.canceled = true;
    }

    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void run() {
        try {
            doRun();
            if (!canceled) {
                service.onWorkerFinished(true, "Arrived at destination.");
            }
        } catch (AutoElytraCanceledException ignored) {
            service.onWorkerFinished(false, "Task canceled.");
        } catch (Throwable throwable) {
            service.onWorkerFinished(false, throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage());
        }
    }

    private void doRun() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            throw new IllegalStateException("Player is not available.");
        }

        service.setStatus(AutoElytraStatus.START, "Configuring Baritone");
        if (module.shouldSetSeed()) {
            try {
                BaritoneAPI.getSettings().elytraNetherSeed.value = Long.parseLong(module.getSeed());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid seed: " + module.getSeed());
            }
        }

        BaritoneAPI.getSettings().elytraAutoJump.value = false;
        BaritoneAPI.getSettings().elytraFireworkSpeed.value = 0.5;
        if (module.getMode() == AutoElytra.Mode.INFINITY_ELYTRA) {
            BaritoneAPI.getSettings().elytraAllowEmergencyLand.value = false;
        }

        service.setStatus(AutoElytraStatus.FLYING, "Starting elytra path");
        service.runOnMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(target));
        service.delayTicks(15);

        while (!canceled) {
            boolean active = service.runOnMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive());
            if (!active) {
                return;
            }
            service.delayTicks(1);
        }

        throw new AutoElytraCanceledException();
    }

    public static final class AutoElytraCanceledException extends RuntimeException {
    }
}

