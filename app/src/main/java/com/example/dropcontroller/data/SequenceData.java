package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Everything the Drops tab edits: data for up to {@link Limits#MAX_DROPS} drops, the camera trigger and
 * three flash triggers. How many drops are active is a setting ({@link AppSettings#getNumDrops()}).
 */
public class SequenceData {

    public static final int FLASH_COUNT = 3;

    private final List<DropData> drops = new ArrayList<>();
    private final CameraTrigger camera = new CameraTrigger();
    private final FlashTrigger[] flashes = new FlashTrigger[FLASH_COUNT];

    public SequenceData() {
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            drops.add(new DropData());
        }
        for (int i = 0; i < FLASH_COUNT; i++) {
            flashes[i] = new FlashTrigger();
        }
    }

    /**
     * @param index zero based drop index (0 is drop 1)
     */
    @NonNull
    public DropData getDrop(int index) {
        return drops.get(index);
    }

    @NonNull
    public CameraTrigger getCamera() {
        return camera;
    }

    /**
     * @param index zero based flash index (0 is flash trigger 1)
     */
    @NonNull
    public FlashTrigger getFlash(int index) {
        return flashes[index];
    }

    /**
     * Updates every value that has a delta (INC adds, DEC subtracts), clamped to its range.
     */
    public void applyDeltas() {
        for (DropData drop : drops) {
            drop.applyDeltas();
        }
        camera.applyDelta();
        for (FlashTrigger flash : flashes) {
            flash.applyDelta();
        }
    }

    public void resetToDefaults() {
        for (DropData drop : drops) {
            drop.resetToDefaults();
        }
        camera.resetToDefaults();
        for (FlashTrigger flash : flashes) {
            flash.resetToDefaults();
        }
    }

    @NonNull
    public SequenceData copy() {
        SequenceData copy = new SequenceData();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(@NonNull SequenceData other) {
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            drops.get(i).copyFrom(other.drops.get(i));
        }
        camera.copyFrom(other.camera);
        for (int i = 0; i < FLASH_COUNT; i++) {
            flashes[i].copyFrom(other.flashes[i]);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SequenceData)) {
            return false;
        }
        SequenceData other = (SequenceData) o;
        return drops.equals(other.drops)
                && camera.equals(other.camera)
                && Arrays.equals(flashes, other.flashes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(drops, camera, Arrays.hashCode(flashes));
    }
}
