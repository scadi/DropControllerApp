package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

/**
 * A message received from the device (already stripped of its square brackets).
 */
public final class DeviceMessage {

    public enum Type {
        /** [HELLO] - reply to the connection request. */
        HELLO,
        /** [YIA] - reply to AYT (Yes I Am). */
        YES_I_AM,
        /** [V,version,compile date] - reply to VERSION. */
        VERSION,
        /** [FTS] - the device finished the sequence (also sent after rejected data). */
        FINISHED,
        /** [M,title,message] - pop-up message. */
        MESSAGE,
        UNKNOWN
    }

    private final Type type;
    private final String version;
    private final String compileDate;
    private final String title;
    private final String message;

    private DeviceMessage(Type type, String version, String compileDate, String title, String message) {
        this.type = type;
        this.version = version;
        this.compileDate = compileDate;
        this.title = title;
        this.message = message;
    }

    @NonNull
    public static DeviceMessage parse(@NonNull String payload) {
        switch (payload) {
            case "HELLO":
                return simple(Type.HELLO);
            case "YIA":
                return simple(Type.YES_I_AM);
            case "FTS":
                return simple(Type.FINISHED);
            default:
                break;
        }

        // Fields are separated by commas; the last field may contain spaces (the compile date does)
        if (payload.startsWith("V,")) {
            String[] parts = payload.split(",", 3);
            return new DeviceMessage(Type.VERSION, parts[1], parts.length > 2 ? parts[2] : "", "", "");
        }
        if (payload.startsWith("M,")) {
            String[] parts = payload.split(",", 3);
            return new DeviceMessage(Type.MESSAGE, "", "", parts[1], parts.length > 2 ? parts[2] : "");
        }
        return simple(Type.UNKNOWN);
    }

    private static DeviceMessage simple(Type type) {
        return new DeviceMessage(type, "", "", "", "");
    }

    @NonNull
    public Type getType() {
        return type;
    }

    /** Firmware version, for {@link Type#VERSION}. */
    @NonNull
    public String getVersion() {
        return version;
    }

    /** Firmware compile date, for {@link Type#VERSION}. */
    @NonNull
    public String getCompileDate() {
        return compileDate;
    }

    /** Pop-up title, for {@link Type#MESSAGE}. */
    @NonNull
    public String getTitle() {
        return title;
    }

    /** Pop-up text, for {@link Type#MESSAGE}. */
    @NonNull
    public String getMessage() {
        return message;
    }
}
