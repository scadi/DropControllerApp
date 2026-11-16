package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Appends the messages exchanged with the device to a file, so the log survives an app restart and can be shared.
 * <p>
 * The file is kept under {@code maxBytes}: when it grows larger, the oldest whole lines are dropped until the file
 * is back to about 75% of the limit. Trimming well below the limit keeps the rewrite rare (once per ~25% of the
 * limit of new data) instead of once per message. All methods are thread-safe. Failures are not thrown (a log must
 * never break the connection); the boolean results and a {@code null} string report them instead.
 * <p>
 * A {@link Listener} is told after every change of the file, so a dialog can show the log live. Listeners are
 * called on the thread that wrote the entry, after the file is written and outside the lock; an exception from a
 * listener is ignored.
 */
public final class ProtocolFileLog {

    public static final int DEFAULT_MAX_BYTES = 200 * 1024;

    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    /** Called after an entry was appended or the file was cleared. Must be cheap and must not throw. */
    public interface Listener {
        void onChanged();
    }

    private final File file;
    private final int maxBytes;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public ProtocolFileLog(@NonNull File file) {
        this(file, DEFAULT_MAX_BYTES);
    }

    public ProtocolFileLog(@NonNull File file, int maxBytes) {
        this.file = file;
        this.maxBytes = Math.max(1, maxBytes);
    }

    public void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** Records one message written to the device, for example {@code [D01...]}. */
    public boolean sent(@NonNull String wireMessage) {
        return sent(wireMessage, System.currentTimeMillis());
    }

    /** Same as {@link #sent(String)} with the time the message was written, for entries recorded later. */
    public boolean sent(@NonNull String wireMessage, long timestampMs) {
        return appendAndNotify("> " + wireMessage, timestampMs);
    }

    /** Records one payload received from the device (without brackets); the brackets are added back. */
    public boolean received(@NonNull String payload) {
        return received(payload, System.currentTimeMillis());
    }

    /** Same as {@link #received(String)} with the time the message arrived, for entries recorded later. */
    public boolean received(@NonNull String payload, long timestampMs) {
        return appendAndNotify("< [" + payload + "]", timestampMs);
    }

    /**
     * Records a connection event (connect, disconnect, failed write), so a shared log shows what the messages
     * around it belong to. Event lines start with {@code #}.
     */
    public boolean event(@NonNull String description) {
        return event(description, System.currentTimeMillis());
    }

    /** Same as {@link #event(String)} with the time of the event, for entries recorded later. */
    public boolean event(@NonNull String description, long timestampMs) {
        return appendAndNotify("# " + description, timestampMs);
    }

    /**
     * @return the whole file, an empty string if it does not exist, or {@code null} if it cannot be read
     */
    @Nullable
    public synchronized String readAll() {
        if (!file.exists()) {
            return "";
        }
        try {
            return new String(readBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Empties the file; it is created if it did not exist. */
    public boolean clear() {
        boolean cleared;
        synchronized (this) {
            try (OutputStream ignored = new FileOutputStream(file, false)) {
                cleared = true;
            } catch (IOException e) {
                cleared = false;
            }
        }
        if (cleared) {
            notifyListeners();
        }
        return cleared;
    }

    @NonNull
    public File getFile() {
        return file;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    private boolean appendAndNotify(@NonNull String line, long timestampMs) {
        boolean appended;
        synchronized (this) {
            appended = append(line, timestampMs);
        }
        // Outside the lock: a slow listener must not block the other writers
        if (appended) {
            notifyListeners();
        }
        return appended;
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            try {
                listener.onChanged();
            } catch (RuntimeException e) {
                // A log listener must never break the connection: BluetoothHelper records messages from its
                // file log executor, and a failing listener must not stop the entries that follow
            }
        }
    }

    // Must be called with "this" locked: it uses the shared SimpleDateFormat and rewrites the file
    private boolean append(@NonNull String line, long timestampMs) {
        String entry = timeFormat.format(new Date(timestampMs)) + " " + line + "\n";
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            try (OutputStream out = new FileOutputStream(file, true)) {
                out.write(entry.getBytes(StandardCharsets.UTF_8));
            }
            trimIfNeeded();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Drops the oldest whole lines until the file is back to about 75% of the limit, but always keeps the last line
    private void trimIfNeeded() throws IOException {
        if (file.length() <= maxBytes) {
            return;
        }
        byte[] bytes = readBytes();
        // Hysteresis: trimming exactly to maxBytes would rewrite the whole file for every new message once the
        // limit is reached, which costs a full read and write per message
        int targetBytes = Math.max(1, maxBytes - maxBytes / 4);
        int keepFrom = Math.max(0, bytes.length - targetBytes);
        while (keepFrom < bytes.length && bytes[keepFrom] != '\n') {
            keepFrom++;
        }
        if (keepFrom < bytes.length) {
            keepFrom++;
        }
        if (keepFrom >= bytes.length) {
            // The limit is smaller than the last line: keep that line anyway
            keepFrom = bytes.length - 1;
            while (keepFrom > 0 && bytes[keepFrom - 1] != '\n') {
                keepFrom--;
            }
        }
        try (OutputStream out = new FileOutputStream(file, false)) {
            out.write(bytes, keepFrom, bytes.length - keepFrom);
        }
    }

    @NonNull
    private byte[] readBytes() throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
