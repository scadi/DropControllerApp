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

/**
 * Appends the messages exchanged with the device to a file, so the log survives an app restart and can be shared.
 * <p>
 * The file is kept under {@code maxBytes}: when it grows larger, the oldest whole lines are dropped until the file
 * is back to about 75% of the limit. Trimming well below the limit keeps the rewrite rare (once per ~25% of the
 * limit of new data) instead of once per message. All methods are thread-safe. Failures are not thrown (a log must
 * never break the connection); the boolean results and a {@code null} string report them instead.
 */
public final class ProtocolFileLog {

    public static final int DEFAULT_MAX_BYTES = 200 * 1024;

    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    private final File file;
    private final int maxBytes;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);

    public ProtocolFileLog(@NonNull File file) {
        this(file, DEFAULT_MAX_BYTES);
    }

    public ProtocolFileLog(@NonNull File file, int maxBytes) {
        this.file = file;
        this.maxBytes = Math.max(1, maxBytes);
    }

    /** Records one message written to the device, for example {@code [D01...]}. */
    public synchronized boolean sent(@NonNull String wireMessage) {
        return append("> " + wireMessage);
    }

    /** Records one payload received from the device (without brackets); the brackets are added back. */
    public synchronized boolean received(@NonNull String payload) {
        return append("< [" + payload + "]");
    }

    /**
     * @return the whole file, an empty string if it does not exist, or {@code null} if it cannot be read
     */
    @Nullable
    public synchronized String readAll() {
        if (!file.exists()) {
            return "";
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Empties the file; it is created if it did not exist. */
    public synchronized boolean clear() {
        try (OutputStream ignored = new FileOutputStream(file, false)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    public File getFile() {
        return file;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    private boolean append(@NonNull String line) {
        String entry = timeFormat.format(new Date()) + " " + line + "\n";
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
        // limit is reached, which costs a full read and write per message on the Bluetooth threads
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
