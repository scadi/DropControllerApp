package com.example.dropcontroller.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.dropcontroller.R;
import com.example.dropcontroller.logic.DeviceMessage;
import com.example.dropcontroller.logic.MessageFramer;
import com.example.dropcontroller.logic.ProtocolEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Bluetooth Classic (SPP/RFCOMM) connection to the dropController (Analogous to BluetoothClient1).
 * <p>
 * One instance is shared by the whole app (see DropControllerApp), so the connection survives tab changes.
 * Connecting and reading run on their own threads, writing runs on a single-thread executor, and all
 * {@link Listener} callbacks are delivered on the main thread.
 */
public class BluetoothHelper {

    private static final String TAG = "BluetoothHelper";

    // Standard Serial Port Profile UUID, used by HC-05/HC-06 modules
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public interface Listener {
        default void onStateChanged(@NonNull State state) {
        }

        default void onMessageReceived(@NonNull DeviceMessage message) {
        }

        default void onError(@StringRes int messageResId) {
        }
    }

    private final Context appContext;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    private volatile State state = State.DISCONNECTED;
    // Incremented by disconnect() so that queued paced writes of an old connection are abandoned
    private volatile int writeGeneration = 0;

    // Guarded by "this"
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    public BluetoothHelper(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        BluetoothManager manager = appContext.getSystemService(BluetoothManager.class);
        this.bluetoothAdapter = manager != null ? manager.getAdapter() : null;
    }

    // ----------------------------------------------------
    // Listeners
    // ----------------------------------------------------

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    // ----------------------------------------------------
    // 1. Connection Discovery (Analogous to ListPicker.Elements)
    // ----------------------------------------------------

    /**
     * @return the paired devices, or an empty set if Bluetooth is off or the permission is missing
     * (the reason is reported to the listeners)
     */
    @NonNull
    @SuppressLint("MissingPermission") // Checked by hasConnectPermission() below
    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            notifyError(R.string.bt_not_enabled);
            return Collections.emptySet();
        }
        if (!hasConnectPermission()) {
            notifyError(R.string.bt_permission_missing);
            return Collections.emptySet();
        }
        try {
            return bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            Log.w(TAG, "Missing Bluetooth permission", e);
            notifyError(R.string.bt_permission_missing);
            return Collections.emptySet();
        }
    }

    /**
     * @return "name\naddress" for a device list entry
     */
    @NonNull
    @SuppressLint("MissingPermission") // Checked by hasConnectPermission() below
    public String getDeviceLabel(@NonNull BluetoothDevice device) {
        String name = null;
        if (hasConnectPermission()) {
            try {
                name = device.getName();
            } catch (SecurityException e) {
                Log.w(TAG, "Missing Bluetooth permission", e);
            }
        }
        if (name == null) {
            name = appContext.getString(R.string.bt_unknown_device);
        }
        return name + "\n" + device.getAddress();
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        // BLUETOOTH and BLUETOOTH_ADMIN are normal permissions on API 30 and below
        return true;
    }

    // ----------------------------------------------------
    // 2. Connection Logic (Analogous to BluetoothClient1.Connect)
    // ----------------------------------------------------

    public synchronized void connect(@NonNull BluetoothDevice device) {
        if (bluetoothAdapter == null || !hasConnectPermission()) {
            notifyError(R.string.bt_permission_missing);
            return;
        }
        cancelThreads();
        setState(State.CONNECTING);
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    /**
     * Closes the connection. Tells the device first ([BYE]) if connected. Does not report an error.
     */
    public synchronized void disconnect() {
        writeGeneration++;
        ConnectedThread closing = connectedThread;
        connectedThread = null;
        cancelThreads();
        if (closing != null) {
            // Send BYE after any queued writes, then close the socket
            try {
                writeExecutor.execute(() -> {
                    closing.write(ProtocolEncoder.bye().getBytes(StandardCharsets.US_ASCII));
                    closing.cancel();
                });
            } catch (RejectedExecutionException e) {
                closing.cancel();
            }
        }
        setState(State.DISCONNECTED);
    }

    /**
     * Cancels the connect and connected threads. Must be called with "this" locked.
     */
    private void cancelThreads() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    // ----------------------------------------------------
    // 3. Data Transfer (Analogous to BluetoothClient1.SendText)
    // ----------------------------------------------------

    /**
     * Sends one message from a background thread. Messages are sent in order.
     *
     * @return false if not connected
     */
    public boolean send(@NonNull String message) {
        return sendAll(Collections.singletonList(message), 0);
    }

    /**
     * Sends the messages in order from a background thread, waiting delayMs between them so the
     * HC-06 and the Arduino SoftwareSerial buffer are not flooded.
     *
     * @return false if not connected
     */
    public boolean sendAll(@NonNull List<String> messages, long delayMs) {
        final ConnectedThread target;
        synchronized (this) {
            target = connectedThread;
        }
        if (target == null) {
            notifyError(R.string.bt_not_connected);
            return false;
        }

        final int generation = writeGeneration;
        final List<String> toSend = List.copyOf(messages);
        try {
            writeExecutor.execute(() -> {
                for (int i = 0; i < toSend.size(); i++) {
                    if (generation != writeGeneration) {
                        return; // The connection was closed; abandon the rest
                    }
                    target.write(toSend.get(i).getBytes(StandardCharsets.US_ASCII));
                    if (delayMs > 0 && i < toSend.size() - 1) {
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Write executor rejected the task", e);
            return false;
        }
        return true;
    }

    // ----------------------------------------------------
    // Inner Thread Classes for Bluetooth Operations
    // ----------------------------------------------------

    // Thread for initiating an outgoing connection
    private class ConnectThread extends Thread {
        private final BluetoothDevice mmDevice;
        private volatile BluetoothSocket mmSocket;
        private volatile boolean cancelled = false;

        ConnectThread(@NonNull BluetoothDevice device) {
            mmDevice = device;
        }

        @SuppressLint("MissingPermission") // Checked in connect()
        @Override
        public void run() {
            BluetoothSocket socket;
            try {
                socket = mmDevice.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException | SecurityException e) {
                Log.w(TAG, "Socket creation failed", e);
                connectionFailed(this);
                return;
            }
            mmSocket = socket;
            if (cancelled) {
                closeQuietly(socket);
                return;
            }

            try {
                // Always cancel discovery because it will slow down a connection
                bluetoothAdapter.cancelDiscovery();
                // Blocks until the connection succeeds or throws an exception
                socket.connect();
            } catch (IOException | SecurityException e) {
                closeQuietly(socket);
                if (!cancelled) {
                    Log.w(TAG, "Connect failed", e);
                    connectionFailed(this);
                }
                return;
            }

            connected(this, socket);
        }

        // Closes the client socket and causes the thread to finish
        void cancel() {
            cancelled = true;
            BluetoothSocket socket = mmSocket;
            if (socket != null) {
                closeQuietly(socket);
            }
        }
    }

    // Thread for handling incoming data. Outgoing data is written from the write executor.
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final MessageFramer framer = new MessageFramer();
        private volatile boolean cancelled = false;

        ConnectedThread(@NonNull BluetoothSocket socket) throws IOException {
            mmSocket = socket;
            mmInStream = socket.getInputStream();
            mmOutStream = socket.getOutputStream();
        }

        @Override
        public void run() {
            byte[] buffer = new byte[256];

            // Keep listening to the InputStream while connected
            while (!cancelled) {
                try {
                    int bytes = mmInStream.read(buffer);
                    if (bytes < 0) {
                        break; // End of stream: the remote side closed the connection
                    }
                    for (String payload : framer.feed(buffer, 0, bytes)) {
                        deliverMessage(DeviceMessage.parse(payload));
                    }
                } catch (IOException e) {
                    break;
                }
            }

            if (!cancelled) {
                connectionLost(this);
            }
        }

        void write(@NonNull byte[] bytes) {
            if (cancelled) {
                return;
            }
            try {
                mmOutStream.write(bytes);
                mmOutStream.flush();
            } catch (IOException e) {
                Log.w(TAG, "Write failed", e);
                notifyError(R.string.bt_send_error);
            }
        }

        void cancel() {
            cancelled = true;
            closeQuietly(mmSocket);
        }
    }

    // ----------------------------------------------------
    // Helper methods for status reporting
    // ----------------------------------------------------

    private synchronized void connected(@NonNull ConnectThread caller, @NonNull BluetoothSocket socket) {
        if (connectThread != caller) {
            // Cancelled or replaced while connecting
            closeQuietly(socket);
            return;
        }
        connectThread = null;

        ConnectedThread thread;
        try {
            thread = new ConnectedThread(socket);
        } catch (IOException e) {
            Log.w(TAG, "Failed to get streams", e);
            closeQuietly(socket);
            setState(State.DISCONNECTED);
            notifyError(R.string.bt_connection_failed);
            return;
        }
        connectedThread = thread;
        thread.start();
        setState(State.CONNECTED);
    }

    private synchronized void connectionFailed(@NonNull ConnectThread caller) {
        if (connectThread != caller) {
            return;
        }
        connectThread = null;
        setState(State.DISCONNECTED);
        notifyError(R.string.bt_connection_failed);
    }

    private synchronized void connectionLost(@NonNull ConnectedThread caller) {
        if (connectedThread != caller) {
            return;
        }
        connectedThread = null;
        writeGeneration++;
        caller.cancel();
        setState(State.DISCONNECTED);
        notifyError(R.string.bt_connection_lost);
    }

    private synchronized void setState(@NonNull State newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onStateChanged(newState);
            }
        });
    }

    private void deliverMessage(@NonNull DeviceMessage message) {
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onMessageReceived(message);
            }
        });
    }

    private void notifyError(@StringRes int messageResId) {
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onError(messageResId);
            }
        });
    }

    private static void closeQuietly(@NonNull BluetoothSocket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore failure during close
        }
    }
}
