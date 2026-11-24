package com.example.dropcontroller.services;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Looks for BLE UART modules (BT05, HM-10, ...) for a few seconds. A module is listed when it advertises the
 * service {@code 0xFFE0} or its name looks like such a module. All callbacks run on the main thread.
 * Needs the Bluetooth scan permission (checked by the caller).
 */
public final class BleScanner {

    private static final String TAG = "BleScanner";

    /** Long enough for a module that advertises every 100 ms to be found, short enough to wait for. */
    public static final long SCAN_DURATION_MS = 8_000;

    private static final ParcelUuid SERIAL_SERVICE = ParcelUuid.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final String[] MODULE_NAME_PARTS = {"bt05", "hm-10", "hm10", "jdy", "ble", "dropcontroller"};

    /** A module found by the scan. */
    public static final class Found {
        @NonNull
        private final BluetoothDevice device;
        @Nullable
        private final String name;
        private final int rssi;

        Found(@NonNull BluetoothDevice device, @Nullable String name, int rssi) {
            this.device = device;
            this.name = name;
            this.rssi = rssi;
        }

        @NonNull
        public BluetoothDevice getDevice() {
            return device;
        }

        /** @return the advertised name, or {@code null} if the module sent none */
        @Nullable
        public String getName() {
            return name;
        }

        /** @return the signal strength in dBm, the higher the nearer */
        public int getRssi() {
            return rssi;
        }
    }

    public interface Listener {
        /** The scan ended (by time or by {@link #stop()}); the modules found, strongest signal first. */
        void onFinished(@NonNull List<Found> found);

        /** The scan could not start (for example Bluetooth is off or the permission is missing). */
        void onFailed(@NonNull String reason);
    }

    private final BluetoothAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Found> found = new ArrayList<>();
    private final Set<String> addresses = new HashSet<>();

    @Nullable
    private BluetoothLeScanner scanner;
    @Nullable
    private Listener listener;
    private boolean running;

    private final Runnable stopByTime = this::stop;

    private final ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            add(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                add(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            fail("the scan failed (error " + errorCode + ")");
        }
    };

    public BleScanner(@Nullable BluetoothAdapter adapter) {
        this.adapter = adapter;
    }

    public boolean isRunning() {
        return running;
    }

    /** Starts a scan that ends by itself after {@link #SCAN_DURATION_MS}. Does nothing if one is running. */
    @SuppressLint("MissingPermission") // The caller checked the Bluetooth scan permission
    public void start(@NonNull Listener newListener) {
        if (running) {
            return;
        }
        listener = newListener;
        found.clear();
        addresses.clear();
        try {
            scanner = adapter != null && adapter.isEnabled() ? adapter.getBluetoothLeScanner() : null;
            if (scanner == null) {
                fail("Bluetooth is off");
                return;
            }
            // No filter: many modules do not put the service in the advertisement; the result is filtered below
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(Collections.<ScanFilter>emptyList(), settings, callback);
        } catch (SecurityException e) {
            Log.w(TAG, "Missing Bluetooth permission", e);
            fail("the Bluetooth permission is missing");
            return;
        }
        running = true;
        mainHandler.postDelayed(stopByTime, SCAN_DURATION_MS);
    }

    /** Ends the scan and reports what was found so far. */
    public void stop() {
        if (!running) {
            return;
        }
        halt();
        Listener target = listener;
        listener = null;
        if (target != null) {
            List<Found> result = new ArrayList<>(found);
            result.sort((a, b) -> Integer.compare(b.getRssi(), a.getRssi()));
            target.onFinished(result);
        }
    }

    /** Ends the scan without reporting anything (the screen is going away). */
    public void cancel() {
        halt();
        listener = null;
    }

    @SuppressLint("MissingPermission") // The caller checked the Bluetooth scan permission
    private void halt() {
        mainHandler.removeCallbacks(stopByTime);
        if (running && scanner != null) {
            try {
                scanner.stopScan(callback);
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "Could not stop the scan", e);
            }
        }
        running = false;
    }

    private void fail(@NonNull String reason) {
        halt();
        Listener target = listener;
        listener = null;
        if (target != null) {
            target.onFailed(reason);
        }
    }

    @SuppressLint("MissingPermission") // The caller checked the Bluetooth permission
    private void add(@NonNull ScanResult result) {
        BluetoothDevice device = result.getDevice();
        String address = device.getAddress();
        if (addresses.contains(address) || !looksLikeSerialModule(result)) {
            return;
        }
        addresses.add(address);
        String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
        found.add(new Found(device, name, result.getRssi()));
    }

    private static boolean looksLikeSerialModule(@NonNull ScanResult result) {
        if (result.getScanRecord() == null) {
            return false;
        }
        List<ParcelUuid> services = result.getScanRecord().getServiceUuids();
        if (services != null && services.contains(SERIAL_SERVICE)) {
            return true;
        }
        String name = result.getScanRecord().getDeviceName();
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        for (String part : MODULE_NAME_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
