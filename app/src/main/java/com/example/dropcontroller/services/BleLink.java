package com.example.dropcontroller.services;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.logic.BleChunker;
import com.example.dropcontroller.logic.ByteQueueInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * {@link DeviceLink} over Bluetooth Low Energy for UART modules such as the BT05 or HM-10: one characteristic
 * (normally {@code 0xFFE1} of service {@code 0xFFE0}) that sends the module's serial data as notifications and
 * takes the app's data as writes. The streams look like a serial port, so {@link BluetoothHelper} reads and writes
 * as it does with an RFCOMM socket.
 * <p>
 * {@link #open} blocks until the link is ready (connected, services found, notifications on); call it from a
 * background thread. Writes are cut into packets of at most 20 bytes and sent one at a time, each after the
 * confirmation of the previous one. The GATT callbacks only queue data and results; no work is done in them.
 */
final class BleLink implements DeviceLink {

    private static final String TAG = "BleLink";

    private static final UUID SERVICE_UUID = uuid16(0xFFE0);
    private static final UUID CHARACTERISTIC_UUID = uuid16(0xFFE1);
    private static final UUID CCCD_UUID = uuid16(0x2902);
    private static final String BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb";

    // Standard services that never carry the serial data (Generic Access, Generic Attribute, Device Information)
    private static final UUID[] STANDARD_SERVICES = {uuid16(0x1800), uuid16(0x1801), uuid16(0x180A)};

    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long STEP_TIMEOUT_MS = 8_000;
    private static final long WRITE_TIMEOUT_MS = 3_000;
    private static final long CLOSE_DELAY_MS = 500;
    private static final long RETRY_DELAY_MS = 500;
    private static final int MAX_CONNECT_ATTEMPTS = 2;
    private static final int MAX_WRITE_START_ATTEMPTS = 5;
    private static final long WRITE_START_RETRY_MS = 20;

    // GATT_ERROR (133) and "connection failed to establish" (62) are the usual transient failures of connectGatt()
    private static final int GATT_ERROR = 133;
    private static final int GATT_CONNECTION_FAILED_TO_ESTABLISH = 62;

    // Put in the results queue when the link goes down while a step waits (the GATT statuses are never negative)
    private static final int DISCONNECTED_MARKER = -1;

    private final Consumer<String> events;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ByteQueueInputStream input = new ByteQueueInputStream();
    private final OutputStream output = new GattOutputStream();
    // The result (a GATT status) of the step that is waited for; steps never overlap
    private final BlockingQueue<Integer> results = new LinkedBlockingQueue<>();
    private final Object writeLock = new Object();
    private final AtomicBoolean gattClosed = new AtomicBoolean(false);

    private volatile BluetoothGatt gatt;
    private volatile BluetoothGattCharacteristic characteristic;
    private volatile int writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
    private volatile boolean disconnected = false;
    private volatile boolean closedByApp = false;
    private volatile int disconnectStatus = BluetoothGatt.GATT_SUCCESS;

    private BleLink(@NonNull Consumer<String> events) {
        this.events = events;
    }

    /**
     * Connects to the module and prepares the serial characteristic. Blocks; interrupting the thread cancels it.
     * Tries once more after the transient GATT error 133.
     *
     * @param events receives readable lines for the communication log, from any thread
     * @throws IOException if the connection cannot be made or the module has no serial characteristic
     */
    @NonNull
    static BleLink open(@NonNull Context context, @NonNull BluetoothDevice device,
                        @NonNull Consumer<String> events) throws IOException {
        for (int attempt = 1; ; attempt++) {
            BleLink link = new BleLink(events);
            try {
                link.connect(context, device);
                return link;
            } catch (IOException e) {
                link.abandon();
                boolean transientError = e instanceof GattException
                        && (((GattException) e).status == GATT_ERROR
                        || ((GattException) e).status == GATT_CONNECTION_FAILED_TO_ESTABLISH);
                if (!transientError || attempt >= MAX_CONNECT_ATTEMPTS || Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                events.accept("GATT error " + ((GattException) e).status + ", trying again");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted before the next attempt", interrupted);
                }
            }
        }
    }

    // ----------------------------------------------------
    // Connection steps
    // ----------------------------------------------------

    @SuppressLint("MissingPermission") // The caller checked the Bluetooth permission
    private void connect(@NonNull Context context, @NonNull BluetoothDevice device) throws IOException {
        try {
            results.clear();
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                throw new IOException("connectGatt returned nothing");
            }
            await("connecting", CONNECT_TIMEOUT_MS);
            events.accept("GATT connected");

            results.clear();
            if (!gatt.discoverServices()) {
                throw new IOException("service discovery could not start");
            }
            await("discovering services", STEP_TIMEOUT_MS);
            characteristic = findSerialCharacteristic(gatt);

            enableNotifications();
            events.accept("notifications enabled");
        } catch (SecurityException e) {
            throw new IOException("Bluetooth permission missing: " + e.getMessage(), e);
        }
    }

    /** The module's serial characteristic: {@code FFE1} of {@code FFE0}, else any one that notifies and can be written. */
    @NonNull
    private BluetoothGattCharacteristic findSerialCharacteristic(@NonNull BluetoothGatt gatt) throws IOException {
        StringBuilder services = new StringBuilder("services:");
        for (BluetoothGattService service : gatt.getServices()) {
            services.append(' ').append(shortUuid(service.getUuid()));
        }
        events.accept(services.toString());

        BluetoothGattCharacteristic found = null;
        BluetoothGattService serial = gatt.getService(SERVICE_UUID);
        if (serial != null) {
            found = serial.getCharacteristic(CHARACTERISTIC_UUID);
        }
        if (found == null) {
            found = findAnySerialCharacteristic(gatt);
        }
        if (found == null) {
            throw new IOException("no serial characteristic (FFE1) on the module");
        }

        int properties = found.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        } else {
            throw new IOException("the serial characteristic cannot be written");
        }
        events.accept("serial characteristic " + shortUuid(found.getUuid()) + ", properties 0x"
                + Integer.toHexString(properties) + ", "
                + (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT ? "write" : "write without response"));
        return found;
    }

    @Nullable
    private static BluetoothGattCharacteristic findAnySerialCharacteristic(@NonNull BluetoothGatt gatt) {
        int notify = BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE;
        int write = BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
        for (BluetoothGattService service : gatt.getServices()) {
            if (isStandardService(service.getUuid())) {
                continue;
            }
            for (BluetoothGattCharacteristic candidate : service.getCharacteristics()) {
                int properties = candidate.getProperties();
                if ((properties & notify) != 0 && (properties & write) != 0) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @SuppressLint("MissingPermission") // The caller checked the Bluetooth permission
    @SuppressWarnings("deprecation") // The pre-API 33 calls are needed for minSdk 24
    private void enableNotifications() throws IOException {
        BluetoothGatt connection = gatt;
        BluetoothGattCharacteristic serial = characteristic;
        if (!connection.setCharacteristicNotification(serial, true)) {
            throw new IOException("notifications could not be turned on");
        }
        BluetoothGattDescriptor cccd = serial.getDescriptor(CCCD_UUID);
        if (cccd == null) {
            // Some modules notify without a descriptor; the notification switch above is then all there is
            events.accept("no CCCD descriptor, notifications are assumed on");
            return;
        }
        byte[] value = (serial.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;

        results.clear();
        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            started = connection.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS;
        } else {
            cccd.setValue(value);
            started = connection.writeDescriptor(cccd);
        }
        if (!started) {
            throw new IOException("the notification switch could not be written");
        }
        await("turning notifications on", STEP_TIMEOUT_MS);
    }

    /** Waits for the result of the current step and throws if it is not a success. */
    private void await(@NonNull String step, long timeoutMs) throws IOException {
        Integer result;
        try {
            result = results.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while " + step, e);
        }
        if (result == null) {
            if (disconnected) {
                throw new GattException("the link went down while " + step, disconnectStatus);
            }
            throw new IOException("timed out while " + step);
        }
        if (result == DISCONNECTED_MARKER) {
            throw new GattException("the link went down while " + step + " (status " + disconnectStatus + ")",
                    disconnectStatus);
        }
        if (result != BluetoothGatt.GATT_SUCCESS) {
            throw new GattException("GATT status " + result + " while " + step, result);
        }
    }

    // ----------------------------------------------------
    // GATT callbacks: only queue results and data
    // ----------------------------------------------------

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                results.offer(status);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onDisconnected(status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            results.offer(status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            results.offer(status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic written, int status) {
            results.offer(status);
        }

        // Android 13 and above
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic changed, @NonNull byte[] value) {
            if (CHARACTERISTIC_UUID.equals(changed.getUuid()) || changed == characteristic) {
                input.offer(value);
            }
        }

        // Android 12 and below
        @SuppressWarnings("deprecation")
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic changed) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return; // The overload with the value is used there
            }
            byte[] value = changed.getValue();
            if (value != null && (CHARACTERISTIC_UUID.equals(changed.getUuid()) || changed == characteristic)) {
                input.offer(value);
            }
        }
    };

    private void onDisconnected(int status) {
        disconnectStatus = status;
        disconnected = true;
        results.offer(DISCONNECTED_MARKER);
        if (!closedByApp) {
            events.accept("GATT disconnected, status " + status);
            input.fail(new IOException("the BLE link went down (GATT status " + status + ")"));
        }
        closeGatt();
    }

    // ----------------------------------------------------
    // DeviceLink
    // ----------------------------------------------------

    @NonNull
    @Override
    public InputStream getInputStream() {
        return input;
    }

    @NonNull
    @Override
    public OutputStream getOutputStream() {
        return output;
    }

    @SuppressLint("MissingPermission") // The permission was checked before the link was opened
    @Override
    public void close() {
        closedByApp = true;
        input.close();
        BluetoothGatt connection = gatt;
        if (connection == null) {
            return;
        }
        try {
            connection.disconnect();
        } catch (SecurityException e) {
            Log.w(TAG, "Missing Bluetooth permission", e);
        }
        // Closing at once can drop the disconnect request; the callback closes the client when the link is down
        mainHandler.postDelayed(this::closeGatt, CLOSE_DELAY_MS);
    }

    /** Drops a link that never became ready: no waiting, the GATT client is released at once. */
    @SuppressLint("MissingPermission") // The permission was checked before the link was opened
    private void abandon() {
        closedByApp = true;
        input.close();
        BluetoothGatt connection = gatt;
        if (connection == null) {
            return;
        }
        try {
            connection.disconnect();
        } catch (SecurityException e) {
            Log.w(TAG, "Missing Bluetooth permission", e);
        }
        closeGatt();
    }

    @SuppressLint("MissingPermission") // The permission was checked before the link was opened
    private void closeGatt() {
        BluetoothGatt connection = gatt;
        if (connection != null && gattClosed.compareAndSet(false, true)) {
            try {
                connection.close();
            } catch (SecurityException e) {
                Log.w(TAG, "Missing Bluetooth permission", e);
            }
        }
    }

    // ----------------------------------------------------
    // Writing: one packet at a time, each confirmed before the next
    // ----------------------------------------------------

    private final class GattOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(@NonNull byte[] data, int offset, int length) throws IOException {
            byte[] bytes = Arrays.copyOfRange(data, offset, offset + length);
            synchronized (writeLock) {
                for (byte[] packet : BleChunker.split(bytes)) {
                    writePacket(packet);
                }
            }
        }
    }

    private void writePacket(@NonNull byte[] packet) throws IOException {
        if (disconnected || closedByApp) {
            throw new IOException("the BLE link is down");
        }
        results.clear();
        for (int attempt = 1; !startWrite(packet); attempt++) {
            // The stack can be busy with the previous operation for a moment
            if (attempt >= MAX_WRITE_START_ATTEMPTS) {
                throw new IOException("the BLE write was not accepted");
            }
            try {
                Thread.sleep(WRITE_START_RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while writing", e);
            }
        }
        await("writing", WRITE_TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission") // The permission was checked before the link was opened
    @SuppressWarnings("deprecation") // The pre-API 33 calls are needed for minSdk 24
    private boolean startWrite(@NonNull byte[] packet) throws IOException {
        BluetoothGatt connection = gatt;
        BluetoothGattCharacteristic serial = characteristic;
        if (connection == null || serial == null) {
            throw new IOException("the BLE link is not ready");
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return connection.writeCharacteristic(serial, packet, writeType) == BluetoothStatusCodes.SUCCESS;
            }
            serial.setValue(packet);
            serial.setWriteType(writeType);
            return connection.writeCharacteristic(serial);
        } catch (SecurityException e) {
            throw new IOException("Bluetooth permission missing: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------

    /** An IOException that remembers the GATT status, so a transient error can be told from a real one. */
    private static final class GattException extends IOException {
        private final int status;

        GattException(@NonNull String message, int status) {
            super(message);
            this.status = status;
        }
    }

    @NonNull
    private static UUID uuid16(int shortUuid) {
        return UUID.fromString(String.format(Locale.US, "0000%04x%s", shortUuid, BASE_UUID_SUFFIX));
    }

    @NonNull
    private static String shortUuid(@NonNull UUID uuid) {
        String text = uuid.toString().toLowerCase(Locale.US);
        if (text.startsWith("0000") && text.endsWith(BASE_UUID_SUFFIX)) {
            return text.substring(4, 8);
        }
        return text;
    }

    private static boolean isStandardService(@NonNull UUID uuid) {
        for (UUID standard : STANDARD_SERVICES) {
            if (standard.equals(uuid)) {
                return true;
            }
        }
        return false;
    }
}
