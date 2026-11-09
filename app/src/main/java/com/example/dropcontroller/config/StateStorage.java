package com.example.dropcontroller.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.DeltaValue;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;

/**
 * Saves and loads {@link AppSettings} and {@link SequenceData} (Analogous to the TinyDB calls of the original app).
 * Missing or invalid values fall back to the defaults. Setters clamp values, so stored data can never be out of range.
 */
public class StateStorage {

    private static final String KEY_NUM_DROPS = "settings.numDrops";
    private static final String KEY_LOOP_MODE = "settings.loopMode";
    private static final String KEY_DELAY_METHOD = "settings.delayMethod";
    private static final String KEY_WAIT_SECONDS = "settings.waitSeconds";
    private static final String KEY_CAMERA_PULSE = "settings.cameraPulseMs";
    private static final String KEY_FLASH_PULSE = "settings.flashPulseMs";
    private static final String KEY_SOUNDS = "settings.sounds";

    private final KeyValueStore store;

    public StateStorage(@NonNull KeyValueStore store) {
        this.store = store;
    }

    public void saveSettings(@NonNull AppSettings settings) {
        store.storeInt(KEY_NUM_DROPS, settings.getNumDrops());
        store.storeBoolean(KEY_LOOP_MODE, settings.isLoopModeOn());
        store.storeString(KEY_DELAY_METHOD, settings.getDelayMethod().name());
        store.storeInt(KEY_WAIT_SECONDS, settings.getWaitSeconds());
        store.storeInt(KEY_CAMERA_PULSE, settings.getCameraPulseMs());
        store.storeInt(KEY_FLASH_PULSE, settings.getFlashPulseMs());
        store.storeBoolean(KEY_SOUNDS, settings.isSoundsOn());
    }

    public void loadSettings(@NonNull AppSettings settings) {
        AppSettings defaults = new AppSettings();
        settings.setNumDrops(store.getInt(KEY_NUM_DROPS, defaults.getNumDrops()));
        settings.setLoopModeOn(store.getBoolean(KEY_LOOP_MODE, defaults.isLoopModeOn()));
        settings.setDelayMethod(parseDelayMethod(store.getString(KEY_DELAY_METHOD, null), defaults.getDelayMethod()));
        settings.setWaitSeconds(store.getInt(KEY_WAIT_SECONDS, defaults.getWaitSeconds()));
        settings.setCameraPulseMs(store.getInt(KEY_CAMERA_PULSE, defaults.getCameraPulseMs()));
        settings.setFlashPulseMs(store.getInt(KEY_FLASH_PULSE, defaults.getFlashPulseMs()));
        settings.setSoundsOn(store.getBoolean(KEY_SOUNDS, defaults.isSoundsOn()));
    }

    public void saveSequence(@NonNull SequenceData data) {
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            String prefix = "drop." + (i + 1) + ".";
            DropData drop = data.getDrop(i);
            store.storeInt(prefix + "sol", drop.getSolenoid());
            store.storeInt(prefix + "start", drop.getStartTime());
            saveDelta(prefix + "startDelta", drop.getStartDelta());
            store.storeInt(prefix + "size", drop.getSize());
            saveDelta(prefix + "sizeDelta", drop.getSizeDelta());
        }

        CameraTrigger camera = data.getCamera();
        store.storeBoolean("camera.enabled", camera.isEnabled());
        store.storeBoolean("camera.bulb", camera.isBulb());
        store.storeInt("camera.time", camera.getTime());
        saveDelta("camera.delta", camera.getDelta());

        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            String prefix = "flash." + (i + 1) + ".";
            FlashTrigger flash = data.getFlash(i);
            store.storeBoolean(prefix + "enabled", flash.isEnabled());
            store.storeInt(prefix + "time", flash.getTime());
            saveDelta(prefix + "delta", flash.getDelta());
        }
    }

    public void loadSequence(@NonNull SequenceData data) {
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            String prefix = "drop." + (i + 1) + ".";
            DropData drop = data.getDrop(i);
            drop.setSolenoid(store.getInt(prefix + "sol", DropData.DEFAULT_SOLENOID));
            drop.setStartTime(store.getInt(prefix + "start", DropData.DEFAULT_START_MS));
            loadDelta(prefix + "startDelta", drop.getStartDelta());
            drop.setSize(store.getInt(prefix + "size", DropData.DEFAULT_SIZE_MS));
            loadDelta(prefix + "sizeDelta", drop.getSizeDelta());
        }

        CameraTrigger camera = data.getCamera();
        camera.setEnabled(store.getBoolean("camera.enabled", false));
        camera.setBulb(store.getBoolean("camera.bulb", false));
        camera.setTime(store.getInt("camera.time", CameraTrigger.DEFAULT_TIME_MS));
        loadDelta("camera.delta", camera.getDelta());

        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            String prefix = "flash." + (i + 1) + ".";
            FlashTrigger flash = data.getFlash(i);
            flash.setEnabled(store.getBoolean(prefix + "enabled", false));
            flash.setTime(store.getInt(prefix + "time", FlashTrigger.DEFAULT_TIME_MS));
            loadDelta(prefix + "delta", flash.getDelta());
        }
    }

    private void saveDelta(@NonNull String prefix, @NonNull DeltaValue delta) {
        store.storeString(prefix + ".dir", delta.getDirection().name());
        store.storeInt(prefix + ".amount", delta.getAmount());
    }

    private void loadDelta(@NonNull String prefix, @NonNull DeltaValue delta) {
        delta.setDirection(parseDirection(store.getString(prefix + ".dir", null)));
        delta.setAmount(store.getInt(prefix + ".amount", 0));
    }

    @NonNull
    private static DeltaDirection parseDirection(@Nullable String name) {
        if (name != null) {
            try {
                return DeltaDirection.valueOf(name);
            } catch (IllegalArgumentException e) {
                // Unknown stored value; use the default
            }
        }
        return DeltaDirection.INC;
    }

    @NonNull
    private static LoopDelayMethod parseDelayMethod(@Nullable String name, @NonNull LoopDelayMethod fallback) {
        if (name != null) {
            try {
                return LoopDelayMethod.valueOf(name);
            } catch (IllegalArgumentException e) {
                // Unknown stored value; use the default
            }
        }
        return fallback;
    }
}
