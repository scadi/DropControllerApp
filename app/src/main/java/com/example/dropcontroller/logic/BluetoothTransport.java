package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How the app talks to a device: Bluetooth Classic (RFCOMM serial port, HC-05 / HC-06) or Bluetooth Low Energy
 * (a UART service, BT05 / HM-10). Also the format of the key saved for the device used last.
 */
public enum BluetoothTransport {
    RFCOMM,
    BLE;

    // Values of android.bluetooth.BluetoothDevice.DEVICE_TYPE_*, copied so this class stays free of Android
    private static final int DEVICE_TYPE_LE = 2;

    private static final String BLE_KEY_PREFIX = "ble:";

    /**
     * The rule for a paired device: only a device that Android knows as LE only uses BLE. Classic, dual mode and
     * unknown devices keep using RFCOMM, as before BLE support.
     *
     * @param deviceType the value of {@code BluetoothDevice.getType()}
     */
    @NonNull
    public static BluetoothTransport forPairedDevice(int deviceType) {
        return deviceType == DEVICE_TYPE_LE ? BLE : RFCOMM;
    }

    /**
     * @return the key saved for the device used last: the plain address for RFCOMM (as saved before BLE support),
     * {@code ble:<address>} for BLE
     */
    @NonNull
    public static String key(@NonNull BluetoothTransport transport, @NonNull String address) {
        return transport == BLE ? BLE_KEY_PREFIX + address : address;
    }

    /** @return the transport in a saved key; a key without the BLE prefix (also an old one) is RFCOMM */
    @NonNull
    public static BluetoothTransport transportOf(@Nullable String key) {
        return key != null && key.startsWith(BLE_KEY_PREFIX) ? BLE : RFCOMM;
    }
}
