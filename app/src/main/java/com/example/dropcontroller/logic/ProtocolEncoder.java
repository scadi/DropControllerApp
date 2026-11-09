package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.SequenceData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the messages sent to the device. Every message is enclosed in square brackets.
 * Formats are defined by the firmware in firmware/ and documented in docs/PROTOCOL.md.
 */
public final class ProtocolEncoder {

    private ProtocolEncoder() {
    }

    @NonNull
    public static String hello() {
        return wrap("HELLO");
    }

    @NonNull
    public static String bye() {
        return wrap("BYE");
    }

    @NonNull
    public static String areYouThere() {
        return wrap("AYT");
    }

    @NonNull
    public static String version() {
        return wrap("VERSION");
    }

    /**
     * Drain: open the valve (1-6).
     */
    @NonNull
    public static String valveOpen(int valve) {
        return wrap("O" + checkValve(valve) + "1");
    }

    /**
     * Drain: close the valve (1-6).
     */
    @NonNull
    public static String valveClose(int valve) {
        return wrap("O" + checkValve(valve) + "0");
    }

    @NonNull
    public static String allValvesClose() {
        return wrap("OX");
    }

    /**
     * Builds the complete message list for one drop sequence, in the order the firmware expects.
     * The device starts the sequence when it receives the last message (EOD).
     *
     * @param sequenceNumber used by the device for debugging only
     */
    @NonNull
    public static List<String> encodeSequence(int sequenceNumber, @NonNull AppSettings settings,
                                              @NonNull SequenceData data) {
        List<String> messages = new ArrayList<>();
        messages.add(wrap("S" + Math.max(0, sequenceNumber)));
        messages.add(wrap("N" + settings.getNumDrops()));

        for (int i = 0; i < settings.getNumDrops(); i++) {
            DropData drop = data.getDrop(i);
            messages.add(wrap("D"
                    + pad(i + 1, 2)
                    + drop.getSolenoid()
                    + pad(drop.getStartTime(), 4)
                    + pad(drop.getStopTime(), 5)));
        }

        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            FlashTrigger flash = data.getFlash(i);
            String prefix = "F" + (i + 1);
            if (flash.isEnabled()) {
                messages.add(wrap(prefix + "Y"
                        + pad(flash.getTime(), 4)
                        + pad(SequenceTiming.flashStop(settings, flash), 5)));
            } else {
                messages.add(wrap(prefix + "N"));
            }
        }

        CameraTrigger camera = data.getCamera();
        if (!camera.isEnabled()) {
            messages.add(wrap("CN"));
        } else {
            messages.add(wrap((camera.isBulb() ? "CB" : "CY")
                    + pad(SequenceTiming.cameraStart(camera), 4)
                    + pad(SequenceTiming.cameraStop(settings, data), 5)));
        }

        messages.add(wrap("EOD"));
        return messages;
    }

    @NonNull
    private static String wrap(@NonNull String payload) {
        return "[" + payload + "]";
    }

    /**
     * Zero padded fixed width decimal. Values are clamped so the width is never exceeded.
     */
    @NonNull
    static String pad(int value, int width) {
        int max = 1;
        for (int i = 0; i < width; i++) {
            max *= 10;
        }
        return String.format(Locale.ROOT, "%0" + width + "d", Limits.clamp(value, 0, max - 1));
    }

    private static int checkValve(int valve) {
        if (valve < Limits.MIN_SOLENOID || valve > Limits.MAX_SOLENOID) {
            throw new IllegalArgumentException("Valve out of range: " + valve);
        }
        return valve;
    }
}
