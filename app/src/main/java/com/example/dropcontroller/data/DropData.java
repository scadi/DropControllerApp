package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Data for a single drop: valve, START time and SIZE (valve open time), each time with a delta.
 * All times are in milliseconds. The drop number is the position in {@link SequenceData}.
 */
public class DropData {

    public static final int DEFAULT_SOLENOID = 1;
    public static final int DEFAULT_START_MS = 100;
    public static final int DEFAULT_SIZE_MS = 30;

    private int solenoid = DEFAULT_SOLENOID;
    private int startTime = DEFAULT_START_MS;
    private final DeltaValue startDelta = new DeltaValue();
    private int size = DEFAULT_SIZE_MS;
    private final DeltaValue sizeDelta = new DeltaValue();

    public int getSolenoid() {
        return solenoid;
    }

    public void setSolenoid(int solenoid) {
        this.solenoid = Limits.clamp(solenoid, Limits.MIN_SOLENOID, Limits.MAX_SOLENOID);
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = Limits.clamp(startTime, Limits.MIN_TIME_MS, Limits.MAX_TIME_MS);
    }

    @NonNull
    public DeltaValue getStartDelta() {
        return startDelta;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Limits.clamp(size, Limits.MIN_TIME_MS, Limits.MAX_TIME_MS);
    }

    @NonNull
    public DeltaValue getSizeDelta() {
        return sizeDelta;
    }

    /**
     * STOP time shown on the Loop Page and sent to the device: START + SIZE.
     */
    public int getStopTime() {
        return startTime + size;
    }

    /**
     * Updates START and SIZE with their deltas (Loop Mode).
     */
    public void applyDeltas() {
        startTime = startDelta.apply(startTime, Limits.MIN_TIME_MS, Limits.MAX_TIME_MS);
        size = sizeDelta.apply(size, Limits.MIN_TIME_MS, Limits.MAX_TIME_MS);
    }

    public void resetToDefaults() {
        solenoid = DEFAULT_SOLENOID;
        startTime = DEFAULT_START_MS;
        startDelta.copyFrom(new DeltaValue());
        size = DEFAULT_SIZE_MS;
        sizeDelta.copyFrom(new DeltaValue());
    }

    @NonNull
    public DropData copy() {
        DropData copy = new DropData();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(@NonNull DropData other) {
        this.solenoid = other.solenoid;
        this.startTime = other.startTime;
        this.startDelta.copyFrom(other.startDelta);
        this.size = other.size;
        this.sizeDelta.copyFrom(other.sizeDelta);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DropData)) {
            return false;
        }
        DropData other = (DropData) o;
        return solenoid == other.solenoid
                && startTime == other.startTime
                && startDelta.equals(other.startDelta)
                && size == other.size
                && sizeDelta.equals(other.sizeDelta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(solenoid, startTime, startDelta, size, sizeDelta);
    }
}
