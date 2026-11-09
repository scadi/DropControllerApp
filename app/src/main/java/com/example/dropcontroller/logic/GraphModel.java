package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.SequenceData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data of the Graph tab: one bar per active drop (valve open from START to STOP), one per enabled flash trigger and
 * one for the camera shutter (open from its START to its STOP, as sent to the device). No Android dependencies.
 */
public final class GraphModel {

    public enum BarType {
        DROP,
        FLASH,
        CAMERA
    }

    /** One horizontal bar: the signal is active from startMs to stopMs. */
    public static final class Bar {
        private final BarType type;
        private final int index;
        private final int startMs;
        private final int stopMs;

        Bar(@NonNull BarType type, int index, int startMs, int stopMs) {
            this.type = type;
            this.index = index;
            this.startMs = startMs;
            this.stopMs = stopMs;
        }

        @NonNull
        public BarType getType() {
            return type;
        }

        /** Drop number or flash trigger number (1 based); 0 for the camera. */
        public int getIndex() {
            return index;
        }

        public int getStartMs() {
            return startMs;
        }

        public int getStopMs() {
            return stopMs;
        }
    }

    private static final int MIN_AXIS_MS = 100;

    private final List<Bar> bars;
    private final int axisMaxMs;
    private final int tickStepMs;

    private GraphModel(List<Bar> bars, int axisMaxMs, int tickStepMs) {
        this.bars = Collections.unmodifiableList(bars);
        this.axisMaxMs = axisMaxMs;
        this.tickStepMs = tickStepMs;
    }

    @NonNull
    public static GraphModel build(@NonNull AppSettings settings, @NonNull SequenceData data) {
        List<Bar> bars = new ArrayList<>();

        for (int i = 0; i < settings.getNumDrops(); i++) {
            DropData drop = data.getDrop(i);
            bars.add(new Bar(BarType.DROP, i + 1, drop.getStartTime(), drop.getStopTime()));
        }
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            FlashTrigger flash = data.getFlash(i);
            if (flash.isEnabled()) {
                bars.add(new Bar(BarType.FLASH, i + 1, flash.getTime(), SequenceTiming.flashStop(settings, flash)));
            }
        }
        CameraTrigger camera = data.getCamera();
        if (camera.isEnabled()) {
            bars.add(new Bar(BarType.CAMERA, 0, SequenceTiming.cameraStart(camera),
                    SequenceTiming.cameraStop(settings, data)));
        }

        int latest = 0;
        for (Bar bar : bars) {
            latest = Math.max(latest, Math.max(bar.getStartMs(), bar.getStopMs()));
        }
        int step = tickStepFor(Math.max(latest, MIN_AXIS_MS));
        // Round the axis up to a whole tick so the last bar does not touch the edge
        int axisMax = ((Math.max(latest, MIN_AXIS_MS) + step - 1) / step) * step;
        return new GraphModel(bars, axisMax, step);
    }

    /**
     * The smallest tick step (1, 2 or 5 times a power of ten) that gives at most 10 ticks.
     */
    static int tickStepFor(int maxMs) {
        int magnitude = 1;
        while (true) {
            for (int factor : new int[]{1, 2, 5}) {
                int step = magnitude * factor;
                if (maxMs <= step * 10) {
                    return step;
                }
            }
            magnitude *= 10;
        }
    }

    @NonNull
    public List<Bar> getBars() {
        return bars;
    }

    /** End of the time axis in milliseconds (a multiple of {@link #getTickStepMs()}). */
    public int getAxisMaxMs() {
        return axisMaxMs;
    }

    public int getTickStepMs() {
        return tickStepMs;
    }
}
