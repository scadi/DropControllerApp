package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.SequenceData;

/**
 * Computes the START/STOP times that are sent to the device. See docs/PROTOCOL.md.
 */
public final class SequenceTiming {

    /** Time the shutter stays open after the last event of a sequence in Bulb mode. */
    public static final int BULB_POST_MS = 100;

    private SequenceTiming() {
    }

    /**
     * Trigger STOP time: START + pulse time, capped to the firmware limit.
     */
    public static int triggerStop(int startTime, int pulseMs) {
        return Math.min(startTime + pulseMs, Limits.MAX_TRIGGER_STOP_MS);
    }

    public static int flashStop(@NonNull AppSettings settings, @NonNull FlashTrigger flash) {
        return triggerStop(flash.getTime(), settings.getFlashPulseMs());
    }

    /**
     * Camera START. In Bulb mode the shutter opens at the start of the sequence.
     */
    public static int cameraStart(@NonNull CameraTrigger camera) {
        return camera.isBulb() ? 0 : camera.getTime();
    }

    /**
     * Camera STOP. In Bulb mode the shutter closes just after the last drop or flash.
     */
    public static int cameraStop(@NonNull AppSettings settings, @NonNull SequenceData data) {
        CameraTrigger camera = data.getCamera();
        if (camera.isBulb()) {
            int stop = latestDropAndFlashStop(settings, data) + BULB_POST_MS;
            return Math.min(stop, Limits.MAX_TRIGGER_STOP_MS);
        }
        return triggerStop(camera.getTime(), settings.getCameraPulseMs());
    }

    /**
     * Latest STOP time of the active drops and the enabled flash triggers.
     */
    public static int latestDropAndFlashStop(@NonNull AppSettings settings, @NonNull SequenceData data) {
        int latest = 0;
        for (int i = 0; i < settings.getNumDrops(); i++) {
            DropData drop = data.getDrop(i);
            latest = Math.max(latest, drop.getStopTime());
        }
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            FlashTrigger flash = data.getFlash(i);
            if (flash.isEnabled()) {
                latest = Math.max(latest, flashStop(settings, flash));
            }
        }
        return latest;
    }

    /**
     * Time at which the device finishes the whole sequence (latest STOP of drops and enabled triggers).
     */
    public static int sequenceEnd(@NonNull AppSettings settings, @NonNull SequenceData data) {
        int end = latestDropAndFlashStop(settings, data);
        if (data.getCamera().isEnabled()) {
            end = Math.max(end, cameraStop(settings, data));
        }
        return end;
    }

    /**
     * How long to wait for the device's [FTS] after sending a sequence: the time to send the messages, the time
     * the device needs for the sequence, and a safety margin.
     *
     * @param messageCount number of messages sent for the sequence
     * @param pacingMs     delay between two messages
     * @param marginMs     extra time after the latest STOP time before giving up
     */
    public static long finishTimeoutMs(@NonNull AppSettings settings, @NonNull SequenceData data,
                                       int messageCount, long pacingMs, long marginMs) {
        return messageCount * pacingMs + sequenceEnd(settings, data) + marginMs;
    }
}
