package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Checks how paired Bluetooth devices are told apart in the device picker. */
public class BluetoothDeviceKindTest {

    private static final int PHONE = 0x0200;
    private static final int COMPUTER = 0x0100;
    private static final int AUDIO = 0x0400;
    private static final int UNCATEGORIZED = 0x1F00;
    private static final int WEARABLE = 0x0700;

    @Test
    public void moduleNames_areLikelyDropController() {
        assertEquals(BluetoothDeviceKind.LIKELY_DROP_CONTROLLER,
                BluetoothDeviceKind.classify("HC-06", UNCATEGORIZED));
        assertEquals(BluetoothDeviceKind.LIKELY_DROP_CONTROLLER,
                BluetoothDeviceKind.classify("hc-05", BluetoothDeviceKind.UNKNOWN_CLASS));
        assertEquals(BluetoothDeviceKind.LIKELY_DROP_CONTROLLER, BluetoothDeviceKind.classify("HC05", PHONE));
        assertEquals(BluetoothDeviceKind.LIKELY_DROP_CONTROLLER,
                BluetoothDeviceKind.classify("  dropController V3 ", UNCATEGORIZED));
        assertEquals(BluetoothDeviceKind.LIKELY_DROP_CONTROLLER,
                BluetoothDeviceKind.classify("HC-06 (lab)", UNCATEGORIZED));
    }

    @Test
    public void similarNames_areNotLikelyDropController() {
        assertEquals(BluetoothDeviceKind.AUDIO, BluetoothDeviceKind.classify("HC-0600 headset", AUDIO));
        assertEquals(BluetoothDeviceKind.PHONE, BluetoothDeviceKind.classify("Galaxy", PHONE));
    }

    @Test
    public void deviceClass_decidesTheKind() {
        assertEquals(BluetoothDeviceKind.PHONE, BluetoothDeviceKind.classify("Pixel", PHONE));
        assertEquals(BluetoothDeviceKind.COMPUTER, BluetoothDeviceKind.classify("Laptop", COMPUTER));
        assertEquals(BluetoothDeviceKind.AUDIO, BluetoothDeviceKind.classify("Buds", AUDIO));
        assertEquals(BluetoothDeviceKind.OTHER, BluetoothDeviceKind.classify("Watch", WEARABLE));
    }

    @Test
    public void unnamedDeviceWithoutClass_isSerialModule() {
        assertEquals(BluetoothDeviceKind.SERIAL_MODULE, BluetoothDeviceKind.classify(null, UNCATEGORIZED));
        assertEquals(BluetoothDeviceKind.SERIAL_MODULE,
                BluetoothDeviceKind.classify(null, BluetoothDeviceKind.UNKNOWN_CLASS));
    }

    @Test
    public void likelyDropController_isSortedFirst() {
        for (BluetoothDeviceKind kind : BluetoothDeviceKind.values()) {
            if (kind != BluetoothDeviceKind.LIKELY_DROP_CONTROLLER) {
                assertTrue(kind.getSortRank() > BluetoothDeviceKind.LIKELY_DROP_CONTROLLER.getSortRank());
            }
        }
    }
}
