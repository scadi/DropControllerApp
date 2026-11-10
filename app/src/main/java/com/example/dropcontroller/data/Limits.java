package com.example.dropcontroller.data;

/**
 * Value ranges of the dropController data. Values outside a range are clamped, never rejected.
 */
public final class Limits {

    public static final int MIN_DROPS = 1;
    public static final int MAX_DROPS = 9;

    public static final int MIN_SOLENOID = 1;
    public static final int MAX_SOLENOID = 6;

    /** START time, SIZE and trigger times are 4 digit values on the wire. */
    public static final int MIN_TIME_MS = 0;
    public static final int MAX_TIME_MS = 9999;

    /** Delta values are 3 digit values in the UI. */
    public static final int MAX_DELTA = 999;

    /** Trigger pulse times and the loop wait time are 3 digit values in the UI. */
    public static final int MAX_PULSE_MS = 999;
    public static final int MAX_WAIT_SECONDS = 999;

    /** STOP times are 5 digit values on the wire. */
    public static final int MAX_STOP_MS = 99999;

    /** The firmware caps trigger STOP times to this value. */
    public static final int MAX_TRIGGER_STOP_MS = 20000;

    private Limits() {
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
