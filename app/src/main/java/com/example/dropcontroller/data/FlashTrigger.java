package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Flash trigger (there are three). OFF (not enabled) or ON with a time in milliseconds and a delta.
 */
public class FlashTrigger {

    public static final int DEFAULT_TIME_MS = 150;

    private boolean enabled;
    private int time = DEFAULT_TIME_MS;
    private final DeltaValue delta = new DeltaValue();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        time = DEFAULT_TIME_MS;
        delta.copyFrom(new DeltaValue());
    }

    @NonNull
    public FlashTrigger copy() {
        FlashTrigger copy = new FlashTrigger();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(@NonNull FlashTrigger other) {
        this.enabled = other.enabled;
        this.time = other.time;
        this.delta.copyFrom(other.delta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FlashTrigger)) {
            return false;
        }
        FlashTrigger other = (FlashTrigger) o;
        return enabled == other.enabled && time == other.time && delta.equals(other.delta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, time, delta);
    }
}
