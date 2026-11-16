package com.example.dropcontroller.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.logic.DeviceSimulator;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * In-process {@link DeviceLink} that answers like a real dropController. Used by the debug-only
 * "Simulated dropController" entry so the whole app can be tested on an emulator or without hardware.
 * <p>
 * The app's writes are fed to {@link DeviceSimulator}; its replies are written back into the app's input stream.
 * The {@code [FTS]} reply is sent after the sequence time reported by the simulator.
 */
final class SimulatorLink implements DeviceLink {

    private static final int PIPE_BUFFER_SIZE = 4096;

    private final PipedInputStream appIn = new PipedInputStream(PIPE_BUFFER_SIZE);
    private final PipedOutputStream appOut;
    private final PipedInputStream deviceIn = new PipedInputStream(PIPE_BUFFER_SIZE);
    private final PipedOutputStream deviceOut;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread timer = new Thread(runnable, "DeviceSimulatorTimer");
        timer.setDaemon(true);
        return timer;
    });
    private final Object writeLock = new Object();
    private final Thread thread;

    private volatile boolean closed;
    @Nullable
    private volatile ScheduledFuture<?> pendingFinish;

    SimulatorLink(@NonNull DeviceSimulator simulator) {
        try {
            // appIn <- deviceOut (messages from the device)
            deviceOut = new PipedOutputStream(appIn);
            // deviceIn <- appOut (messages from the app)
            appOut = new PipedOutputStream(deviceIn);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create the simulator pipes", e);
        }
        thread = new Thread(() -> run(simulator), "DeviceSimulator");
        thread.setDaemon(true);
        thread.start();
    }

    @NonNull
    @Override
    public InputStream getInputStream() {
        return appIn;
    }

    @NonNull
    @Override
    public OutputStream getOutputStream() {
        return appOut;
    }

    @Override
    public void close() {
        closed = true;
        cancelPendingFinish();
        scheduler.shutdownNow();
        thread.interrupt();
        closeQuietly(deviceIn);
        closeQuietly(appOut);
        closeQuietly(appIn);
        closeQuietly(deviceOut);
    }

    private void run(@NonNull DeviceSimulator simulator) {
        byte[] buffer = new byte[256];
        try {
            while (!closed) {
                int bytes = deviceIn.read(buffer);
                if (bytes < 0) {
                    break;
                }
                DeviceSimulator.Result result = simulator.feed(buffer, 0, bytes);
                write(result.getMessages());
                if (result.getFinishDelayMs() != DeviceSimulator.NO_FINISH) {
                    scheduleFinish(result.getFinishDelayMs());
                }
            }
        } catch (IOException e) {
            // The link was closed or the app stopped reading
        }
    }

    private void scheduleFinish(long delayMs) {
        if (closed) {
            return;
        }
        // A new sequence replaces the pending [FTS]: the old one must not arrive early or twice
        cancelPendingFinish();
        try {
            pendingFinish = scheduler.schedule(
                    () -> write(Collections.singletonList(DeviceSimulator.FINISHED_REPLY)),
                    delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The link was closed while scheduling
        }
    }

    private void cancelPendingFinish() {
        ScheduledFuture<?> pending = pendingFinish;
        if (pending != null) {
            pending.cancel(false);
            pendingFinish = null;
        }
    }

    private void write(@NonNull List<String> messages) {
        if (messages.isEmpty()) {
            return;
        }
        synchronized (writeLock) {
            try {
                for (String message : messages) {
                    deviceOut.write(message.getBytes(StandardCharsets.US_ASCII));
                }
                deviceOut.flush();
            } catch (IOException e) {
                // The link was closed or the app stopped reading
            }
        }
    }

    private static void closeQuietly(@NonNull Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // Ignore failure during close
        }
    }
}
