package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.Limits;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Behavioural model of the dropController V3 firmware (firmware/*.ino, docs/PROTOCOL.md), for testing the app
 * without hardware. Feed it the bytes the app would send over Bluetooth; it answers with the messages the device
 * would send back.
 * <p>
 * Pure Java: no Android and no threads, so it can be used from JVM unit tests and from {@code SimulatorLink}.
 * A sequence starts when {@code [EOD]} arrives and the data is complete, like {@code dropDataIsOK()} in the
 * firmware. Accepted data finishes after the latest STOP time of the sequence; rejected data finishes at once.
 * The caller is responsible for sending {@link #FINISHED_REPLY} after {@link Result#getFinishDelayMs()}.
 */
public final class DeviceSimulator {

    /** Version reported to {@code [VERSION]}; matches firmware/dropControllerV3_dC3_AS25006.ino. */
    public static final String DEFAULT_VERSION = "dC3_AS25006";
    public static final String DEFAULT_COMPILE_DATE = "Jan  1 2025";

    /** The device's end-of-sequence message. */
    public static final String FINISHED_REPLY = "[FTS]";

    /** {@link Result#getFinishDelayMs()} when the received bytes did not end a sequence. */
    public static final long NO_FINISH = -1;

    private static final int DROP_MESSAGE_LENGTH = 13;
    private static final int FLASH_MESSAGE_LENGTH = 12;
    private static final int CAMERA_MESSAGE_LENGTH = 11;

    private final MessageFramer framer = new MessageFramer();
    private final Set<Integer> openValves = new TreeSet<>();
    private final List<String> currentSequence = new ArrayList<>();
    private final boolean[] dropSeen = new boolean[Limits.MAX_DROPS + 1];
    private final int[] dropStopMs = new int[Limits.MAX_DROPS + 1];

    private final String version;
    private final String compileDate;

    private boolean connected;
    private List<String> lastSequence = Collections.emptyList();

    private int declaredDrops;
    private int receivedDrops;
    private boolean haveFlash1;
    private boolean haveFlash2;
    private boolean haveFlash3;
    private boolean haveCamera;
    private int latestStopMs;
    private boolean lastEodAccepted;

    public DeviceSimulator() {
        this(DEFAULT_VERSION, DEFAULT_COMPILE_DATE);
    }

    public DeviceSimulator(@NonNull String version, @NonNull String compileDate) {
        this.version = version;
        this.compileDate = compileDate;
    }

    /**
     * Feeds one complete or partial message.
     *
     * @return the immediate replies and, when the bytes ended a sequence, the delay before {@code [FTS]}
     */
    @NonNull
    public Result feed(@NonNull String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return feed(bytes, 0, bytes.length);
    }

    /**
     * Feeds a chunk of the byte stream, as read from the Bluetooth socket.
     *
     * @return the immediate replies and, when the bytes ended a sequence, the delay before {@code [FTS]}
     */
    @NonNull
    public Result feed(@NonNull byte[] data, int offset, int length) {
        List<String> messages = new ArrayList<>();
        long finishDelayMs = NO_FINISH;
        boolean accepted = false;
        for (String payload : framer.feed(data, offset, length)) {
            long handled = handle(payload, messages);
            if (handled != NO_FINISH) {
                finishDelayMs = handled;
                accepted = lastEodAccepted;
            }
        }
        return new Result(messages, finishDelayMs, accepted);
    }

    /** @return the valves the device currently holds open (drain commands) */
    @NonNull
    public Set<Integer> getOpenValves() {
        return Collections.unmodifiableSet(new TreeSet<>(openValves));
    }

    /** @return true between {@code [HELLO]} and {@code [BYE]} */
    public boolean isConnected() {
        return connected;
    }

    /** @return the payloads of the last complete sequence, in the order they were received (without brackets) */
    @NonNull
    public List<String> getLastSequence() {
        return lastSequence;
    }

    /** Forgets all state, as if the device had just been powered on. */
    public void reset() {
        framer.reset();
        openValves.clear();
        connected = false;
        lastSequence = Collections.emptyList();
        resetSequence();
    }

    /**
     * @return the delay before {@code [FTS]}, or {@link #NO_FINISH} if the message did not end a sequence
     */
    private long handle(@NonNull String payload, @NonNull List<String> messages) {
        if (payload.isEmpty()) {
            return NO_FINISH;
        }
        switch (payload.charAt(0)) {
            case 'H':
                if ("HELLO".equals(payload)) {
                    connected = true;
                    messages.add("[HELLO]");
                }
                return NO_FINISH;
            case 'B':
                if ("BYE".equals(payload)) {
                    connected = false;
                }
                return NO_FINISH;
            case 'A':
                if ("AYT".equals(payload)) {
                    messages.add("[YIA]");
                }
                return NO_FINISH;
            case 'V':
                if ("VERSION".equals(payload)) {
                    messages.add("[V," + version + "," + compileDate + "]");
                }
                return NO_FINISH;
            case 'O':
                handleValve(payload);
                return NO_FINISH;
            case 'S':
                // Any S... message starts a sequence (the firmware only uses it as a debug marker)
                resetSequence();
                connected = true;
                return NO_FINISH;
            case 'N':
                declaredDrops = digitAt(payload, 1);
                return NO_FINISH;
            case 'D':
                handleDrop(payload);
                return NO_FINISH;
            case 'F':
                handleFlash(payload);
                return NO_FINISH;
            case 'C':
                handleCamera(payload);
                return NO_FINISH;
            case 'E':
                return "EOD".equals(payload) ? endOfData() : NO_FINISH;
            default:
                // Unknown messages are ignored, like in the firmware
                return NO_FINISH;
        }
    }

    private void handleValve(@NonNull String payload) {
        if (payload.length() < 2) {
            return;
        }
        if (payload.charAt(1) == 'X') {
            openValves.clear();
            return;
        }
        if (payload.length() < 3) {
            return;
        }
        int valve = digitAt(payload, 1);
        if (valve < Limits.MIN_SOLENOID || valve > Limits.MAX_SOLENOID) {
            return;
        }
        char onOff = payload.charAt(2);
        if (onOff == '1') {
            openValves.add(valve);
        } else if (onOff == '0') {
            openValves.remove(valve);
        }
    }

    private void handleDrop(@NonNull String payload) {
        currentSequence.add(payload);
        // The firmware counts every D message, even a malformed one
        receivedDrops++;
        if (payload.length() < DROP_MESSAGE_LENGTH) {
            return;
        }
        int dropNumber = numberAt(payload, 1, 2);
        if (dropNumber < 1 || dropNumber > Limits.MAX_DROPS) {
            return;
        }
        dropSeen[dropNumber] = true;
        dropStopMs[dropNumber] = numberAt(payload, 8, 5);
    }

    private void handleFlash(@NonNull String payload) {
        currentSequence.add(payload);
        if (payload.length() < 3) {
            return;
        }
        switch (digitAt(payload, 1)) {
            case 1:
                haveFlash1 = true;
                break;
            case 2:
                haveFlash2 = true;
                break;
            case 3:
                haveFlash3 = true;
                break;
            default:
                return;
        }
        if (payload.charAt(2) == 'N' || payload.length() < FLASH_MESSAGE_LENGTH) {
            return;
        }
        latestStopMs = Math.max(latestStopMs, cappedTriggerStop(numberAt(payload, 7, 5)));
    }

    private void handleCamera(@NonNull String payload) {
        currentSequence.add(payload);
        if (payload.length() < 2) {
            return;
        }
        haveCamera = true;
        char mode = payload.charAt(1);
        if (mode == 'N' || payload.length() < CAMERA_MESSAGE_LENGTH) {
            return;
        }
        latestStopMs = Math.max(latestStopMs, cappedTriggerStop(numberAt(payload, 6, 5)));
    }

    /**
     * @return the delay before {@code [FTS]}, or {@link #NO_FINISH} if this was not the end of the data
     */
    private long endOfData() {
        lastEodAccepted = receivedDrops == declaredDrops
                && haveFlash1 && haveFlash2 && haveFlash3 && haveCamera;
        lastSequence = Collections.unmodifiableList(new ArrayList<>(currentSequence));
        long delay = lastEodAccepted ? sequenceEndMs() : 0;
        resetSequence();
        return delay;
    }

    private int sequenceEndMs() {
        int end = latestStopMs;
        for (int drop = 1; drop <= Limits.MAX_DROPS; drop++) {
            if (dropSeen[drop]) {
                end = Math.max(end, dropStopMs[drop]);
            }
        }
        return end;
    }

    private void resetSequence() {
        declaredDrops = 0;
        receivedDrops = 0;
        haveFlash1 = false;
        haveFlash2 = false;
        haveFlash3 = false;
        haveCamera = false;
        latestStopMs = 0;
        Arrays.fill(dropSeen, false);
        Arrays.fill(dropStopMs, 0);
        currentSequence.clear();
    }

    private static int cappedTriggerStop(int stopMs) {
        return Math.min(stopMs, Limits.MAX_TRIGGER_STOP_MS);
    }

    private static int digitAt(@NonNull String text, int index) {
        if (index >= text.length()) {
            return 0;
        }
        char c = text.charAt(index);
        return c >= '0' && c <= '9' ? c - '0' : 0;
    }

    private static int numberAt(@NonNull String text, int start, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            int index = start + i;
            if (index >= text.length()) {
                break;
            }
            char c = text.charAt(index);
            if (c < '0' || c > '9') {
                return value;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    /**
     * What the device did with one chunk of received bytes.
     */
    public static final class Result {

        private final List<String> messages;
        private final long finishDelayMs;
        private final boolean sequenceAccepted;

        private Result(@NonNull List<String> messages, long finishDelayMs, boolean sequenceAccepted) {
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
            this.finishDelayMs = finishDelayMs;
            this.sequenceAccepted = sequenceAccepted;
        }

        /** Replies the device sends at once, for example {@code [HELLO]}, {@code [YIA]} or {@code [V,...]}. */
        @NonNull
        public List<String> getMessages() {
            return messages;
        }

        /**
         * How long the device runs the sequence before {@code [FTS]}, or {@link DeviceSimulator#NO_FINISH} if the
         * bytes did not end a sequence. Rejected data has a delay of 0.
         */
        public long getFinishDelayMs() {
            return finishDelayMs;
        }

        /** Only meaningful when {@link #getFinishDelayMs()} is not {@link DeviceSimulator#NO_FINISH}. */
        public boolean wasSequenceAccepted() {
            return sequenceAccepted;
        }
    }
}
