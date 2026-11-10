package com.example.dropcontroller.services;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.logic.DeviceMessage;
import com.example.dropcontroller.logic.ProtocolEncoder;
import com.example.dropcontroller.logic.SequenceTiming;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends one drop sequence to the device and tracks whether it is running.
 * <p>
 * The device does not report an "active" state (see docs/PROTOCOL.md): the sequence is ACTIVE from sending the
 * data until the device answers [FTS], the connection drops, or a timeout expires. One instance is shared by the
 * whole app (see DropControllerApp), so the state survives tab changes. All callbacks run on the main thread.
 */
public class SequenceRunner {

    /** Delay between two messages, so the HC-06 and the SoftwareSerial buffer are not flooded. */
    public static final long MESSAGE_PACING_MS = 30;

    /** Extra time after the latest STOP time of the sequence before giving up waiting for [FTS]. */
    public static final long FINISH_TIMEOUT_MARGIN_MS = 5000;

    public interface Listener {
        /** The sequence became active (true) or idle (false). */
        void onActiveChanged(boolean active);

        /**
         * The device reported the end of the sequence ([FTS]). Not called for a timeout, a lost connection or
         * {@link #abort()}. Called before {@link #onFinished}.
         */
        default void onDeviceFinished() {
        }

        /**
         * The sequence is over.
         *
         * @param timedOut true if [FTS] never arrived and the timeout ended the sequence
         */
        default void onFinished(boolean timedOut) {
        }
    }

    private final BluetoothHelper bluetoothHelper;
    private final AppSettings settings;
    private final SequenceData data;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Runnable timeoutRunnable = () -> finish(true);

    private boolean active;

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            if (state != BluetoothHelper.State.CONNECTED && active) {
                // The connection is gone, so [FTS] will never arrive
                finish(false);
            }
        }

        @Override
        public void onMessageReceived(@NonNull DeviceMessage message) {
            if (message.getType() == DeviceMessage.Type.FINISHED && active) {
                for (Listener listener : listeners) {
                    listener.onDeviceFinished();
                }
                finish(false);
            }
        }
    };

    public SequenceRunner(@NonNull BluetoothHelper bluetoothHelper, @NonNull AppSettings settings,
                          @NonNull SequenceData data) {
        this.bluetoothHelper = bluetoothHelper;
        this.settings = settings;
        this.data = data;
        // Lives as long as the app, like the BluetoothHelper itself
        bluetoothHelper.addListener(bluetoothListener);
    }

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Sends the current settings and the shared drop data. Must be called on the main thread.
     *
     * @param sequenceNumber used by the device for debugging only
     * @return false if a sequence is already running or the device is not connected
     */
    public boolean start(int sequenceNumber) {
        return start(sequenceNumber, data);
    }

    /**
     * Sends the current settings and the given drop data (Loop Mode sends its own working copy).
     * Must be called on the main thread.
     *
     * @param sequenceNumber used by the device for debugging only
     * @return false if a sequence is already running or the device is not connected
     */
    public boolean start(int sequenceNumber, @NonNull SequenceData sequenceData) {
        if (active || !bluetoothHelper.isConnected()) {
            return false;
        }
        List<String> messages = ProtocolEncoder.encodeSequence(sequenceNumber, settings, sequenceData);
        if (!bluetoothHelper.sendAll(messages, MESSAGE_PACING_MS)) {
            return false;
        }

        long sendTimeMs = messages.size() * MESSAGE_PACING_MS;
        long timeoutMs = sendTimeMs + SequenceTiming.sequenceEnd(settings, sequenceData) + FINISH_TIMEOUT_MARGIN_MS;
        setActive(true);
        mainHandler.postDelayed(timeoutRunnable, timeoutMs);
        return true;
    }

    /**
     * Forgets the running sequence without waiting for the device (for example when the app is reset).
     */
    public void abort() {
        if (active) {
            finish(false);
        }
    }

    private void finish(boolean timedOut) {
        mainHandler.removeCallbacks(timeoutRunnable);
        setActive(false);
        for (Listener listener : listeners) {
            listener.onFinished(timedOut);
        }
    }

    private void setActive(boolean newActive) {
        if (active == newActive) {
            return;
        }
        active = newActive;
        for (Listener listener : listeners) {
            listener.onActiveChanged(newActive);
        }
    }
}
