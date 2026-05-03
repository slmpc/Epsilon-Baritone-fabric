package com.github.slmpc.epsilon_baritone.elytra;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.slmpc.epsilon_baritone.EpsilonBaritoneMod;
import com.github.slmpc.epsilon_baritone.modules.AutoElytra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class AutoElytraService {

    public static final AutoElytraService INSTANCE = new AutoElytraService();

    public static final Item[] FOOD_LIST = {
            Items.GOLDEN_CARROT,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.COOKED_BEEF,
            Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN
    };

    private final AtomicReference<MainThreadTaskHolder<?>> currentTask = new AtomicReference<>();
    private final PriorityQueue<ScheduledTask> scheduledTasks = new PriorityQueue<>(Comparator.<ScheduledTask>comparingInt(task -> task.nextRunTick).thenComparingInt(task -> -task.priority));
    private final Object threadLock = new Object();

    private boolean subscribed;
    private boolean clientHooksInitialized;
    private int currentTick;

    private @Nullable AutoElytraWorker worker;
    private @Nullable AutoElytra module;
    private @Nullable BlockPos target;
    private AutoElytraStatus status = AutoElytraStatus.IDLE;
    private int startTick;
    private BlockPos startPos = BlockPos.ZERO;
    private String lastStatusMessage = "Idle";

    private boolean autoDisconnectEnabled;

    private boolean cameraOverride;
    private boolean fixEyeHeight;
    private float fixedYaw;
    private float fixedPitch;
    private boolean lookControlFlag;
    private boolean[] pausedState;
    private boolean lookMixinReady;
    private boolean pauseMixinReady;

    private AutoElytraService() {
    }

    public void initialize() {
        if (!subscribed) {
            EventBus.INSTANCE.subscribe(this);
            subscribed = true;
        }
    }

    public void initializeClientHooks() {
        if (clientHooksInitialized) {
            return;
        }
        clientHooksInitialized = true;
    }

    public synchronized void start(@NotNull AutoElytra module, @NotNull BlockPos target) {
        if (isRunning()) {
            sendMessage(AutoElytraMessageLevel.WARNING, "Auto Elytra is already running.");
            return;
        }

        this.module = module;
        this.target = target;
        this.status = AutoElytraStatus.START;
        this.startTick = currentTick;
        this.lastStatusMessage = "Starting";
        this.autoDisconnectEnabled = module.isLowHealthAutoDisconnect();

        if (Minecraft.getInstance().player != null) {
            this.startPos = Minecraft.getInstance().player.blockPosition();
        }

        this.worker = new AutoElytraWorker(this, module, target);
        this.worker.start();
        sendMessage(AutoElytraMessageLevel.INFO, "Started Auto Elytra to " + target.getX() + ", " + target.getY() + ", " + target.getZ());
    }

    public synchronized void stop(String reason) {
        AutoElytraWorker currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.cancel();
        }
        worker = null;
        status = AutoElytraStatus.CANCELED;
        cameraOverride = false;
        fixEyeHeight = false;
        TrajectoryRenderer.clear();
        sendMessage(AutoElytraMessageLevel.WARNING, reason);
    }

    public synchronized boolean isRunning() {
        return worker != null && worker.isAlive();
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public AutoElytraStatus getStatus() {
        return status;
    }

    public void setStatus(AutoElytraStatus status, String message) {
        this.status = status;
        this.lastStatusMessage = message;
    }

    public void onWorkerFinished(boolean success, String message) {
        if (module != null && success && module.isAutoDisconnectOnFinish()) {
            disconnect("[AutoElytra] " + message);
        } else if (module != null && !success && module.isAutoDisconnectOnFailure()) {
            disconnect("[AutoElytra] " + message);
        }
        this.status = success ? AutoElytraStatus.FINISHED : AutoElytraStatus.FAILED;
        this.lastStatusMessage = message;
        this.worker = null;
        this.cameraOverride = false;
        this.fixEyeHeight = false;
        TrajectoryRenderer.clear();
        sendMessage(success ? AutoElytraMessageLevel.INFO : AutoElytraMessageLevel.ERROR, message);
    }

    public <T> T runOnMainThread(Supplier<T> supplier) {
        AutoElytraWorker currentWorker = worker;
        if (Thread.currentThread() != currentWorker || currentWorker == null) {
            return supplier.get();
        }

        CountDownLatch latch = new CountDownLatch(1);
        MainThreadTaskHolder<T> holder = new MainThreadTaskHolder<>(supplier, latch);
        if (!currentTask.compareAndSet(null, holder)) {
            throw new IllegalStateException("Only one main-thread task can be queued at a time.");
        }

        try {
            latch.await();
            return holder.getResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for main-thread task.", e);
        }
    }

    public void runOnMainThread(Runnable runnable) {
        runOnMainThread(() -> {
            runnable.run();
            return null;
        });
    }

    public void delayTicks(int ticks) {
        AutoElytraWorker currentWorker = worker;
        if (Thread.currentThread() != currentWorker || currentWorker == null) {
            return;
        }

        for (int i = 0; i < ticks; i++) {
            if (currentWorker.isCanceled()) {
                throw new AutoElytraWorker.AutoElytraCanceledException();
            }
            try {
                synchronized (threadLock) {
                    threadLock.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AutoElytraWorker.AutoElytraCanceledException();
            }
        }
    }

    public ScheduledTask scheduleTask(TaskConsumer action, int period, int repeatTimes, int delay, int priority, Object... args) {
        ScheduledTask task = new ScheduledTask(action, Math.max(1, period), repeatTimes, currentTick + delay, priority, args);
        scheduledTasks.add(task);
        return task;
    }

    public void sendMessage(AutoElytraMessageLevel level, String message) {
        if (level == AutoElytraMessageLevel.DEBUG && (module == null || !module.isDebugMessages())) {
            return;
        }
        if (level == AutoElytraMessageLevel.TIP || level == AutoElytraMessageLevel.INFO || level == AutoElytraMessageLevel.WARNING || level == AutoElytraMessageLevel.ERROR || level == AutoElytraMessageLevel.FATAL) {
            ChatUtils.addChatMessage("[Auto Elytra] " + message);
        }
        if (level == AutoElytraMessageLevel.DEBUG && module != null && module.isDebugMessages()) {
            ChatUtils.addChatMessage("[Auto Elytra/Debug] " + message);
        }
        switch (level) {
            case DEBUG -> EpsilonBaritoneMod.LOGGER.debug(message);
            case TIP, INFO -> EpsilonBaritoneMod.LOGGER.info(message);
            case WARNING -> EpsilonBaritoneMod.LOGGER.warn(message);
            case ERROR, FATAL -> EpsilonBaritoneMod.LOGGER.error(message);
        }
    }

    public Item getSelectedFood() {
        if (module == null) {
            return Items.GOLDEN_CARROT;
        }
        return switch (module.getFoodType()) {
            case GOLDEN_APPLE -> Items.GOLDEN_APPLE;
            case ENCHANTED_GOLDEN_APPLE -> Items.ENCHANTED_GOLDEN_APPLE;
            case COOKED_BEEF -> Items.COOKED_BEEF;
            case COOKED_PORKCHOP -> Items.COOKED_PORKCHOP;
            case COOKED_CHICKEN -> Items.COOKED_CHICKEN;
            default -> Items.GOLDEN_CARROT;
        };
    }

    public void setCameraOverride(boolean cameraOverride, float yaw, float pitch) {
        this.cameraOverride = cameraOverride;
        this.fixedYaw = yaw;
        this.fixedPitch = pitch;
    }

    public boolean isCameraOverride() {
        return cameraOverride && module != null && module.isHideRecoveryCamera();
    }

    public float getFixedYaw() {
        return fixedYaw;
    }

    public float getFixedPitch() {
        return fixedPitch;
    }

    public void setFixEyeHeight(boolean fixEyeHeight) {
        this.fixEyeHeight = fixEyeHeight;
    }

    public boolean shouldFixEyeHeight() {
        return fixEyeHeight;
    }

    public void markBaritoneLookControlled() {
        this.lookControlFlag = true;
    }

    public boolean consumeLookControlFlag() {
        return lookControlFlag;
    }

    public void setPausedState(boolean[] pausedState) {
        this.pausedState = pausedState;
        this.pauseMixinReady = true;
    }

    public boolean isBaritonePaused() {
        return pausedState != null && pausedState.length > 0 && pausedState[0];
    }

    public void setLookMixinReady() {
        this.lookMixinReady = true;
    }

    public boolean isLookMixinReady() {
        return lookMixinReady;
    }

    public boolean isPauseMixinReady() {
        return pauseMixinReady;
    }

    public void triggerInfinityElytraCycle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        setFixEyeHeight(true);
        scheduleTask((self, args) -> setFixEyeHeight(false), 1, 0, 3, 100000);
        minecraft.player.stopFallFlying();
        minecraft.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(minecraft.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    public void disconnect(String message) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
            Minecraft.getInstance().player.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal(message)));
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onTick(TickEvent.Pre event) {
        currentTick++;

        while (!scheduledTasks.isEmpty() && scheduledTasks.peek().nextRunTick <= currentTick) {
            ScheduledTask task = scheduledTasks.poll();
            task.action.accept(task, task.args);
            if (task.repeatTimes > 0 || task.repeatTimes == -1) {
                if (task.repeatTimes > 0) {
                    task.repeatTimes--;
                }
                task.nextRunTick = currentTick + task.period;
                scheduledTasks.add(task);
            }
        }

        AutoElytraWorker currentWorker = worker;
        if (currentWorker != null) {
            synchronized (threadLock) {
                threadLock.notifyAll();
            }
            MainThreadTaskHolder<?> holder = currentTask.getAndSet(null);
            if (holder != null) {
                holder.execute();
            }
        }

        if (currentWorker != null && module != null && module.getMode() == AutoElytra.Mode.INFINITY_ELYTRA && Minecraft.getInstance().player != null) {
            boolean activeFlight = Minecraft.getInstance().player.isFallFlying() && (status == AutoElytraStatus.FLYING || status == AutoElytraStatus.LANDING);
            if (activeFlight && currentTick % 16 == 0) {
                triggerInfinityElytraCycle();
            }
            if (activeFlight && currentTick % 16 == 1 && Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().player.startFallFlying();
                Minecraft.getInstance().getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(Minecraft.getInstance().player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }

            if (autoDisconnectEnabled && Minecraft.getInstance().player.getHealth() < 3.5F) {
                int totemCount = 0;
                for (int i = 0; i < 45; i++) {
                    if (Minecraft.getInstance().player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                        totemCount += Minecraft.getInstance().player.getInventory().getItem(i).getCount();
                    }
                }
                if (totemCount <= 1) {
                    autoDisconnectEnabled = false;
                    stop("Low health auto disconnect triggered.");
                }
            }
        }

        lookControlFlag = false;
    }

    public void renderHud(GuiGraphicsExtractor graphics) {
        if (module == null || !module.isHudEnabled() || Minecraft.getInstance().player == null || !isRunning()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Mode: " + module.getMode().name());
        lines.add("Status: " + status.name());
        lines.add("Distance Flown: " + String.format("%.2f", getFlyDistance()));
        lines.add("Distance Left: " + String.format("%.2f", getRemainDistance()));
        lines.add("Average Speed: " + String.format("%.2f m/s", getAverageSpeed()));
        lines.add("ETA: " + formatEtaSeconds(getRemainSeconds()));
        lines.add(lastStatusMessage);

        for (int i = 0; i < lines.size(); i++) {
            graphics.text(Minecraft.getInstance().font, lines.get(i), module.getHudX(), module.getHudY() + 10 * i, 0xFFFFFFFF);
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onRender3D(Render3DEvent event) {
        if (module != null && module.isRenderTrajectory()) {
            TrajectoryRenderer.render(event.getPoseStack());
        }
    }

    public double getRemainDistance() {
        if (target == null || Minecraft.getInstance().player == null) {
            return -1;
        }
        return Math.sqrt(target.distSqr(Minecraft.getInstance().player.blockPosition()));
    }

    public double getFlyDistance() {
        if (Minecraft.getInstance().player == null) {
            return -1;
        }
        return Math.sqrt(startPos.distSqr(Minecraft.getInstance().player.blockPosition()));
    }

    public double getAverageSpeed() {
        int elapsedTicks = Math.max(1, currentTick - startTick);
        return getFlyDistance() / elapsedTicks * 20.0;
    }

    public double getRemainSeconds() {
        double speed = getAverageSpeed();
        if (speed <= 0.0) {
            return -1;
        }
        return getRemainDistance() / speed;
    }

    private String formatEtaSeconds(double seconds) {
        if (seconds < 0 || Double.isInfinite(seconds) || Double.isNaN(seconds)) {
            return "--:--:--";
        }
        int total = (int) seconds;
        int hour = total / 3600;
        total %= 3600;
        int minute = total / 60;
        int second = total % 60;
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    private static final class MainThreadTaskHolder<T> {
        private final Supplier<T> supplier;
        private final CountDownLatch latch;
        private T result;
        private Throwable error;

        private MainThreadTaskHolder(Supplier<T> supplier, CountDownLatch latch) {
            this.supplier = supplier;
            this.latch = latch;
        }

        private void execute() {
            try {
                result = supplier.get();
            } catch (Throwable throwable) {
                error = throwable;
            } finally {
                latch.countDown();
            }
        }

        private T getResult() {
            if (error != null) {
                throw new IllegalStateException(error);
            }
            return result;
        }
    }

    public static final class ScheduledTask {
        private final TaskConsumer action;
        private final int period;
        private int repeatTimes;
        private int nextRunTick;
        private final int priority;
        private final Object[] args;

        private ScheduledTask(TaskConsumer action, int period, int repeatTimes, int nextRunTick, int priority, Object[] args) {
            this.action = action;
            this.period = period;
            this.repeatTimes = repeatTimes;
            this.nextRunTick = nextRunTick;
            this.priority = priority;
            this.args = args;
        }
    }

    @FunctionalInterface
    public interface TaskConsumer {
        void accept(ScheduledTask self, Object[] args);
    }
}



