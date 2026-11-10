package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Camera trigger. Modes: OFF (not enabled), ON + shutter time, ON + Bulb.
 * In Bulb mode the shutter opens at the start of the sequence and closes just after it;
 * the time and its delta are not used.
 */
public class CameraTrigger {

    public static final int DEFAULT_TIME_MS = 0;

    private boolean enabled;
    private boolean bulb;
    private int time = DEFAULT_TIME_MS;
    private final DeltaValue delta = new DeltaValue();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBulb() {
        return bulb;
    }

    public void setBulb(boolean bulb) {
        this.bulb = bulb;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = Limits.clamp(time, Limits.MIN_TIME_MS, Limits.MAX_TIME_MS);
    }

    @NonNull
    public DeltaValue getDelta() {
        return delta;
    }

    public void applyDelta() {
        time = delta.apply(time, Limits.MIN_TIME_MS, Limits.MAX_TIME_MS);
    }

    public void resetToDefaults() {
        enabled = false;
        bulb = false;
        time = DEFAULT_TIME_MS;
        delta.copyFrom(new DeltaValue());
    }

    @NonNull
    public CameraTrigger copy() {
        CameraTrigger copy = new CameraTrigger();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(@NonNull CameraTrigger other) {
        this.enabled = other.enabled;
        this.bulb = other.bulb;
        this.time = other.time;
        this.delta.copyFrom(other.delta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CameraTrigger)) {
            return false;
        }
        CameraTrigger other = (CameraTrigger) o;
        return enabled == other.enabled && bulb == other.bulb && time == other.time && delta.equals(other.delta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, bulb, time, delta);
    }
}
