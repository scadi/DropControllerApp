package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * General settings from the Settings tab (the camera and flash enable switches live in {@link SequenceData}).
 */
public class AppSettings {

    public static final int DEFAULT_NUM_DROPS = 1;
    public static final int DEFAULT_WAIT_SECONDS = 10;
    public static final int DEFAULT_PULSE_MS = 50;

    private int numDrops = DEFAULT_NUM_DROPS;
    private boolean loopModeOn;
    private LoopDelayMethod delayMethod = LoopDelayMethod.TIMER;
    private int waitSeconds = DEFAULT_WAIT_SECONDS;
    private int cameraPulseMs = DEFAULT_PULSE_MS;
    private int flashPulseMs = DEFAULT_PULSE_MS;
    private boolean soundsOn = true;
    // A device preference: not part of a preset and not changed by RESET
    private ThemeMode themeMode = ThemeMode.SYSTEM;

    public int getNumDrops() {
        return numDrops;
    }

    public void setNumDrops(int numDrops) {
        this.numDrops = Limits.clamp(numDrops, Limits.MIN_DROPS, Limits.MAX_DROPS);
    }

    public boolean isLoopModeOn() {
        return loopModeOn;
    }

    public void setLoopModeOn(boolean loopModeOn) {
        this.loopModeOn = loopModeOn;
    }

    @NonNull
    public LoopDelayMethod getDelayMethod() {
        return delayMethod;
    }

    public void setDelayMethod(@NonNull LoopDelayMethod delayMethod) {
        this.delayMethod = delayMethod;
    }

    public int getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = Limits.clamp(waitSeconds, 0, Limits.MAX_WAIT_SECONDS);
    }

    public int getCameraPulseMs() {
        return cameraPulseMs;
    }

    public void setCameraPulseMs(int cameraPulseMs) {
        this.cameraPulseMs = Limits.clamp(cameraPulseMs, 0, Limits.MAX_PULSE_MS);
    }

    public int getFlashPulseMs() {
        return flashPulseMs;
    }

    public void setFlashPulseMs(int flashPulseMs) {
        this.flashPulseMs = Limits.clamp(flashPulseMs, 0, Limits.MAX_PULSE_MS);
    }

    public boolean isSoundsOn() {
        return soundsOn;
    }

    public void setSoundsOn(boolean soundsOn) {
        this.soundsOn = soundsOn;
    }

    @NonNull
    public ThemeMode getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(@NonNull ThemeMode themeMode) {
        this.themeMode = themeMode;
    }

    /**
     * Restores the defaults of the app settings. The theme is kept: it is a device preference, and switching it
     * on RESET would be a surprise.
     */
    public void resetToDefaults() {
        numDrops = DEFAULT_NUM_DROPS;
        loopModeOn = false;
        delayMethod = LoopDelayMethod.TIMER;
        waitSeconds = DEFAULT_WAIT_SECONDS;
        cameraPulseMs = DEFAULT_PULSE_MS;
        flashPulseMs = DEFAULT_PULSE_MS;
        soundsOn = true;
    }

    /**
     * Copies the settings that describe a setup: everything except the Sounds switch, which is a device preference.
     */
    public void copySetupFrom(@NonNull AppSettings other) {
        numDrops = other.numDrops;
        loopModeOn = other.loopModeOn;
        delayMethod = other.delayMethod;
        waitSeconds = other.waitSeconds;
        cameraPulseMs = other.cameraPulseMs;
        flashPulseMs = other.flashPulseMs;
    }

    /**
     * Compares the settings that describe a setup (see {@link #copySetupFrom(AppSettings)}).
     */
    public boolean hasSameSetupAs(@NonNull AppSettings other) {
        return numDrops == other.numDrops
                && loopModeOn == other.loopModeOn
                && delayMethod == other.delayMethod
                && waitSeconds == other.waitSeconds
                && cameraPulseMs == other.cameraPulseMs
                && flashPulseMs == other.flashPulseMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppSettings)) {
            return false;
        }
        AppSettings other = (AppSettings) o;
        return numDrops == other.numDrops
                && loopModeOn == other.loopModeOn
                && delayMethod == other.delayMethod
                && waitSeconds == other.waitSeconds
                && cameraPulseMs == other.cameraPulseMs
                && flashPulseMs == other.flashPulseMs
                && soundsOn == other.soundsOn
                && themeMode == other.themeMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numDrops, loopModeOn, delayMethod, waitSeconds, cameraPulseMs, flashPulseMs, soundsOn,
                themeMode);
    }
}
