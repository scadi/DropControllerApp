package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory log of the messages exchanged with the device, for checking the wire protocol without hardware.
 * <p>
 * Sent messages are stored exactly as they are written (with brackets); received payloads are stored with the
 * brackets added back. The log keeps the last {@code capacity} entries. Listeners are called on the thread that
 * records the entry, so a UI listener must post to the main thread itself. An exception from one listener is
 * ignored, so a broken listener cannot break the connection or the other listeners.
 */
public final class ProtocolLog {

    public static final int DEFAULT_CAPACITY = 200;

    public enum Direction {
        SENT,
        RECEIVED
    }

    public interface Listener {
        void onChanged();
    }

    private final int capacity;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public ProtocolLog() {
        this(DEFAULT_CAPACITY);
    }

    public ProtocolLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Records one message written to the device, for example {@code [D01...]}. */
    public void sent(@NonNull String wireMessage) {
        add(Direction.SENT, wireMessage);
    }

    /** Records one payload received from the device (without brackets); the brackets are added back. */
    public void received(@NonNull String payload) {
        add(Direction.RECEIVED, "[" + payload + "]");
    }

    /** @return the entries in the order they were recorded, oldest first */
    @NonNull
    public synchronized List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void clear() {
        synchronized (this) {
            entries.clear();
        }
        notifyListeners();
    }

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    private void add(@NonNull Direction direction, @NonNull String message) {
        synchronized (this) {
            entries.add(new Entry(direction, message, System.currentTimeMillis()));
            while (entries.size() > capacity) {
                entries.remove(0);
            }
        }
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            try {
                listener.onChanged();
            } catch (RuntimeException e) {
                // A log listener must never break the connection: BluetoothHelper records messages on its
                // read and write threads, where an escaping exception would end the read loop
            }
        }
    }

    /** One message sent to or received from the device. */
    public static final class Entry {

        private final Direction direction;
        private final String message;
        private final long timestampMs;

        private Entry(@NonNull Direction direction, @NonNull String message, long timestampMs) {
            this.direction = direction;
            this.message = message;
            this.timestampMs = timestampMs;
        }

        @NonNull
        public Direction getDirection() {
            return direction;
        }

        public boolean isSent() {
            return direction == Direction.SENT;
        }

        @NonNull
        public String getMessage() {
            return message;
        }

        public long getTimestampMs() {
            return timestampMs;
        }
    }
}
