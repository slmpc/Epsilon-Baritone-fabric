package com.github.slmpc.epsilon_baritone;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import com.github.epsilon.events.bus.EpsilonEventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.tick.TickEvent;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringSetting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BaritoneAddonSettings {

    private final Map<String, Settings.Setting<?>> baritoneSettings = collectBaritoneSettings();
    private final List<Binding> bindings = new ArrayList<>();

    private boolean subscribed;

    BaritoneAddonSettings(
            BoolSettingFactory boolFactory,
            IntSettingFactory intFactory,
            DoubleSettingFactory doubleFactory,
            StringSettingFactory stringFactory
    ) {
        registerBoolean("allowBreak", boolFactory);
        registerBoolean("allowPlace", boolFactory);
        registerBoolean("allowInventory", boolFactory);
        registerBoolean("allowSprint", boolFactory);
        registerBoolean("allowParkour", boolFactory);
        registerBoolean("autoTool", boolFactory);
        registerBoolean("assumeStep", boolFactory);
        registerBoolean("assumeWalkOnWater", boolFactory);
        registerBoolean("assumeWalkOnLava", boolFactory);
        registerBoolean("freeLook", boolFactory);
        registerBoolean("renderGoal", boolFactory);
        registerBoolean("renderPath", boolFactory);
        registerBoolean("enterPortal", boolFactory);
        registerBoolean("rightClickContainerOnArrival", boolFactory);
        registerBoolean("itemSaver", boolFactory);
        registerBoolean("chatControl", boolFactory);
        registerBoolean("prefixControl", boolFactory);
        registerNumber("blockReachDistance", 0.0, 10.0, 0.1, intFactory, doubleFactory);
        registerNumber("maxFallHeightNoWater", 0.0, 255.0, 1.0, intFactory, doubleFactory);
        registerNumber("maxFallHeightBucket", 0.0, 255.0, 1.0, intFactory, doubleFactory);
        registerNumber("mineGoalUpdateInterval", 1.0, 100.0, 1.0, intFactory, doubleFactory);
        registerNumber("pathCutoffFactor", 0.0, 1.0, 0.01, intFactory, doubleFactory);
        registerPrefix(stringFactory);
    }

    void start() {
        syncToBaritone();

        if (!subscribed) {
            EpsilonEventBus.INSTANCE.subscribe(this);
            subscribed = true;
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onTick(TickEvent.Pre event) {
        syncToBaritone();
    }

    private void syncToBaritone() {
        for (Binding binding : bindings) {
            binding.sync();
        }
    }

    private Map<String, baritone.api.Settings.Setting<?>> collectBaritoneSettings() {
        Map<String, baritone.api.Settings.Setting<?>> settingsByName = new HashMap<>();

        try {
            Class<? extends baritone.api.Settings> klass = BaritoneAPI.getSettings().getClass();

            for (Field field : klass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(BaritoneAPI.getSettings());
                if (value instanceof baritone.api.Settings.Setting<?> setting) {
                    settingsByName.put(setting.getName(), setting);
                }
            }
        } catch (IllegalAccessException exception) {
            throw new RuntimeException("Failed to collect Baritone settings.", exception);
        }

        return settingsByName;
    }

    private void registerBoolean(String name, BoolSettingFactory factory) {
        baritone.api.Settings.Setting<?> setting = lookupSetting(name, Boolean.class);
        if (setting == null) {
            return;
        }

        boolean initialValue = resolveBooleanValue(setting);
        BoolSetting addonSetting = factory.create(name, initialValue);
        bindings.add(() -> applyIfChanged(setting, addonSetting.getValue()));
    }

    private void registerPrefix(StringSettingFactory factory) {
        String name = "prefix";
        baritone.api.Settings.Setting<?> setting = lookupSetting(name, String.class);
        if (setting == null) {
            return;
        }

        String initialValue = resolveStringValue(setting);
        StringSetting addonSetting = factory.create(name, initialValue);
        bindings.add(() -> applyIfChanged(setting, addonSetting.getValue()));
    }

    private void registerNumber(
            String name,
            double min,
            double max,
            double step,
            IntSettingFactory intFactory,
            DoubleSettingFactory doubleFactory
    ) {
        baritone.api.Settings.Setting<?> setting = baritoneSettings.get(name);
        if (setting == null) {
            EpsilonBaritoneMod.LOGGER.warn("Skipped missing Baritone setting '{}' while building addon settings.", name);
            return;
        }

        Object initialValue = setting.value != null ? setting.value : setting.defaultValue;

        if (initialValue instanceof Integer integerValue) {
            IntSetting addonSetting = intFactory.create(name, integerValue, clampToInt(min), clampToInt(max), clampStep(step));
            bindings.add(() -> applyIfChanged(setting, addonSetting.getValue()));
            return;
        }

        if (initialValue instanceof Long longValue) {
            IntSetting addonSetting = intFactory.create(name, safeLongToInt(name, longValue), clampToInt(min), clampToInt(max), clampStep(step));
            bindings.add(() -> applyIfChanged(setting, addonSetting.getValue().longValue()));
            return;
        }

        if (initialValue instanceof Float floatValue) {
            DoubleSetting addonSetting = doubleFactory.create(name, floatValue.doubleValue(), min, max, step);
            bindings.add(() -> applyIfChanged(setting, addonSetting.getValue().floatValue()));
            return;
        }

        if (initialValue instanceof Double doubleValue) {
            DoubleSetting addonSetting = doubleFactory.create(name, doubleValue, min, max, step);
            bindings.add(() -> applyIfChanged(setting, addonSetting.getValue()));
            return;
        }

        EpsilonBaritoneMod.LOGGER.warn(
                "Skipped unsupported numeric Baritone setting '{}' of type {}.",
                name,
                initialValue == null ? "null" : initialValue.getClass().getName()
        );
    }

    private baritone.api.Settings.Setting<?> lookupSetting(String name, Class<?> expectedType) {
        baritone.api.Settings.Setting<?> setting = baritoneSettings.get(name);
        if (setting == null) {
            EpsilonBaritoneMod.LOGGER.warn("Skipped missing Baritone setting '{}' while building addon settings.", name);
            return null;
        }

        Object value = setting.value != null ? setting.value : setting.defaultValue;
        if (value != null && !expectedType.isInstance(value)) {
            EpsilonBaritoneMod.LOGGER.warn(
                    "Skipped Baritone setting '{}' because {} is not compatible with {}.",
                    name,
                    value.getClass().getName(),
                    expectedType.getName()
            );
            return null;
        }

        return setting;
    }

    private boolean resolveBooleanValue(baritone.api.Settings.Setting<?> setting) {
        Object value = setting.value != null ? setting.value : setting.defaultValue;
        return value instanceof Boolean bool && bool;
    }

    private String resolveStringValue(baritone.api.Settings.Setting<?> setting) {
        Object value = setting.value != null ? setting.value : setting.defaultValue;
        return value instanceof String string ? string : "";
    }

    private void applyIfChanged(baritone.api.Settings.Setting<?> setting, Object newValue) {
        if (!Objects.equals(setting.value, newValue)) {
            setRawValue(setting, newValue);
        }
    }

    @SuppressWarnings("rawtypes")
    private void setRawValue(baritone.api.Settings.Setting setting, Object newValue) {
        setting.value = newValue;
    }

    private int clampToInt(double value) {
        return Math.clamp(Math.round(value), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    private int clampStep(double step) {
        return Math.max(1, (int) Math.round(step));
    }

    private int safeLongToInt(String name, long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Baritone setting '" + name + "' is outside the supported int range: " + value);
        }
        return (int) value;
    }

    @FunctionalInterface
    private interface Binding {
        void sync();
    }

    @FunctionalInterface
    interface BoolSettingFactory {
        BoolSetting create(String name, boolean defaultValue);
    }

    @FunctionalInterface
    interface IntSettingFactory {
        IntSetting create(String name, int defaultValue, int min, int max, int step);
    }

    @FunctionalInterface
    interface DoubleSettingFactory {
        DoubleSetting create(String name, double defaultValue, double min, double max, double step);
    }

    @FunctionalInterface
    interface StringSettingFactory {
        StringSetting create(String name, String defaultValue);
    }
}




