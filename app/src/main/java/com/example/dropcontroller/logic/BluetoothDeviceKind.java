package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The kind of a paired Bluetooth device, guessed from its name and its major device class, so the device picker can
 * show an icon and a hint and list the devices that can be a dropController first.
 */
public enum BluetoothDeviceKind {
    /** The name looks like the HC-05 / HC-06 module of the dropController (or the device is called so). */
    LIKELY_DROP_CONTROLLER(0),
    /** A serial module or another device without a specific class. */
    SERIAL_MODULE(1),
    PHONE(2),
    COMPUTER(2),
    AUDIO(3),
    OTHER(3);

    // Values of android.bluetooth.BluetoothClass.Device.Major, copied so this class stays free of Android
    private static final int MAJOR_MISC = 0x0000;
    private static final int MAJOR_COMPUTER = 0x0100;
    private static final int MAJOR_PHONE = 0x0200;
    private static final int MAJOR_AUDIO_VIDEO = 0x0400;
    private static final int MAJOR_UNCATEGORIZED = 0x1F00;

    /** Passed when the device reports no class. */
    public static final int UNKNOWN_CLASS = -1;

    private static final Pattern DROP_CONTROLLER_NAME =
            Pattern.compile("^(hc-?0[56]\\b.*|.*drop\\s*controller.*)$");

    private final int sortRank;

    BluetoothDeviceKind(int sortRank) {
        this.sortRank = sortRank;
    }

    /**
     * @return the position of the kind in the device list: the lower, the nearer to the top
     */
    public int getSortRank() {
        return sortRank;
    }

    /**
     * @param name       the device name, {@code null} if it has none
     * @param majorClass the major device class, {@link #UNKNOWN_CLASS} if the device reports none
     */
    @NonNull
    public static BluetoothDeviceKind classify(@Nullable String name, int majorClass) {
        if (name != null && DROP_CONTROLLER_NAME.matcher(name.trim().toLowerCase(Locale.US)).matches()) {
            return LIKELY_DROP_CONTROLLER;
        }
        switch (majorClass) {
            case MAJOR_PHONE:
                return PHONE;
            case MAJOR_COMPUTER:
                return COMPUTER;
            case MAJOR_AUDIO_VIDEO:
                return AUDIO;
            case MAJOR_UNCATEGORIZED:
            case MAJOR_MISC:
            case UNKNOWN_CLASS:
                return SERIAL_MODULE;
            default:
                return OTHER;
        }
    }
}
