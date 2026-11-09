package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;

/**
 * State machine of Loop Mode (Analogous to the Loop Page logic and Clock_LoopDelayTimer of the original app).
 * <p>
 * It has no Android dependencies: the owner calls {@link #onSecondTick()} once per second, reports the end of a
 * sequence with {@link #onSequenceFinished()} (device [FTS] or timeout) and sends the sequence in
 * {@link Listener#onSendSequence}. It works on a copy of the drop data, so the Drops tab is never modified.
 * <p>
 * TIMER: after each sequence a countdown (the wait time) runs, then the next sequence starts automatically.
 * BUTTON: after each sequence the controller waits for {@link #start()}.
 * After every sequence the deltas are applied. STOP pauses at the current sequence number, RESET sets the
 * sequence number back to 1 and the drop data back to the start values.
 */
public class LoopController {

    public enum State {
        /** Not started, or paused by STOP. */
        IDLE,
        /** A sequence was sent; waiting for the device to finish. */
        SEQUENCE_ACTIVE,
        /** TIMER mode: counting down to the next sequence. */
        COUNTDOWN,
        /** BUTTON mode: waiting for the user to start the next sequence. */
        WAITING_FOR_BUTTON
    }

    public interface Listener {
        /** Send this sequence to the device. The data is a copy that the listener may keep. */
        void onSendSequence(int sequenceNumber, @NonNull SequenceData data);

        void onStateChanged(@NonNull State state);

        void onCountdown(int secondsRemaining);

        /** The values or the sequence number changed (after deltas were applied or after a reset). */
        void onDataChanged(int sequenceNumber, @NonNull SequenceData data);
    }

    private final AppSettings settings;
    private final SequenceData startValues;
    private final SequenceData working;
    private final Listener listener;

    private State state = State.IDLE;
    private int sequenceNumber = 1;
    private int secondsRemaining = 0;
    private boolean stopRequested = false;

    public LoopController(@NonNull AppSettings settings, @NonNull SequenceData source, @NonNull Listener listener) {
        this.settings = settings;
        this.startValues = source.copy();
        this.working = source.copy();
        this.listener = listener;
    }

    @NonNull
    public State getState() {
        return state;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    /** The values that will be used for the next sequence. Do not modify. */
    @NonNull
    public SequenceData getData() {
        return working;
    }

    /**
     * Starts (or continues after STOP) the loop, or starts the next sequence in BUTTON mode.
     *
     * @return false if the controller is busy and ignored the call
     */
    public boolean start() {
        if (state != State.IDLE && state != State.WAITING_FOR_BUTTON) {
            return false;
        }
        beginSequence();
        return true;
    }

    /**
     * Pauses at the current sequence number. While a sequence is active the pause happens when it finishes.
     */
    public void stop() {
        if (state == State.SEQUENCE_ACTIVE) {
            stopRequested = true;
        } else if (state == State.COUNTDOWN || state == State.WAITING_FOR_BUTTON) {
            secondsRemaining = 0;
            setState(State.IDLE);
        }
    }

    /**
     * Sets the sequence number back to 1 and the drop data back to the start values.
     *
     * @return false if a sequence is active and the call was ignored
     */
    public boolean reset() {
        if (state == State.SEQUENCE_ACTIVE) {
            return false;
        }
        sequenceNumber = 1;
        secondsRemaining = 0;
        stopRequested = false;
        working.copyFrom(startValues);
        setState(State.IDLE);
        listener.onDataChanged(sequenceNumber, working);
        return true;
    }

    /**
     * Call when the device finished the sequence ([FTS]) or when the wait for it timed out.
     */
    public void onSequenceFinished() {
        if (state != State.SEQUENCE_ACTIVE) {
            return;
        }
        working.applyDeltas();
        sequenceNumber++;
        listener.onDataChanged(sequenceNumber, working);

        if (stopRequested) {
            stopRequested = false;
            setState(State.IDLE);
        } else if (settings.getDelayMethod() == LoopDelayMethod.TIMER) {
            if (settings.getWaitSeconds() > 0) {
                secondsRemaining = settings.getWaitSeconds();
                setState(State.COUNTDOWN);
                listener.onCountdown(secondsRemaining);
            } else {
                beginSequence();
            }
        } else {
            setState(State.WAITING_FOR_BUTTON);
        }
    }

    /**
     * Call once per second.
     */
    public void onSecondTick() {
        if (state != State.COUNTDOWN) {
            return;
        }
        secondsRemaining--;
        if (secondsRemaining <= 0) {
            secondsRemaining = 0;
            beginSequence();
        } else {
            listener.onCountdown(secondsRemaining);
        }
    }

    /**
     * Call when the connection to the device is lost. The loop stops at the current sequence number.
     */
    public void onConnectionLost() {
        stopRequested = false;
        secondsRemaining = 0;
        setState(State.IDLE);
    }

    private void beginSequence() {
        stopRequested = false;
        setState(State.SEQUENCE_ACTIVE);
        listener.onSendSequence(sequenceNumber, working.copy());
    }

    private void setState(State newState) {
        if (state != newState) {
            state = newState;
            listener.onStateChanged(newState);
        }
    }
}
