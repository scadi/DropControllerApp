package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A delta: INC/DEC plus an amount (0 is off). Applied after every sequence in Loop Mode.
 */
public class DeltaValue {

    private DeltaDirection direction;
    private int amount;

    public DeltaValue() {
        this(DeltaDirection.INC, 0);
    }

    public DeltaValue(@NonNull DeltaDirection direction, int amount) {
        this.direction = direction;
        this.amount = Limits.clamp(amount, 0, Limits.MAX_DELTA);
    }

    @NonNull
    public DeltaDirection getDirection() {
        return direction;
    }

    public void setDirection(@NonNull DeltaDirection direction) {
        this.direction = direction;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Limits.clamp(amount, 0, Limits.MAX_DELTA);
    }

    public boolean isActive() {
        return amount > 0;
    }

    /**
     * Adds (INC) or subtracts (DEC) the amount. The result is clamped to min..max, never rejected.
     */
    public int apply(int value, int min, int max) {
        int signedAmount = direction == DeltaDirection.INC ? amount : -amount;
        return Limits.clamp(value + signedAmount, min, max);
    }

    @NonNull
    public DeltaValue copy() {
        return new DeltaValue(direction, amount);
    }

    public void copyFrom(@NonNull DeltaValue other) {
        this.direction = other.direction;
        this.amount = other.amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeltaValue)) {
            return false;
        }
        DeltaValue other = (DeltaValue) o;
        return direction == other.direction && amount == other.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(direction, amount);
    }
}
