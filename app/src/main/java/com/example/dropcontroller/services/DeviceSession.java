package com.example.dropcontroller.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.logic.DeviceMessage;
import com.example.dropcontroller.logic.ProtocolEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handshake and device information. When the connection opens it sends [HELLO] (the device stops blinking its waiting
 * LED) and [VERSION]; the reply to [VERSION] is kept here for the About tab. Replies are not required for the
 * connection to be usable (docs/PROTOCOL.md, decision 6). One instance is shared by the whole app.
 * All callbacks run on the main thread.
 */
public class DeviceSession {

    public interface Listener {
        /** The firmware version became known, or was cleared after a disconnect. */
        void onFirmwareChanged(@Nullable String version, @Nullable String compileDate);
    }

    private final BluetoothHelper bluetoothHelper;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private String firmwareVersion;
    private String compileDate;

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            if (state == BluetoothHelper.State.CONNECTED) {
                bluetoothHelper.sendAll(
                        Arrays.asList(ProtocolEncoder.hello(), ProtocolEncoder.version()),
                        SequenceRunner.MESSAGE_PACING_MS);
            } else if (firmwareVersion != null) {
                setFirmware(null, null);
            }
        }

        @Override
        public void onMessageReceived(@NonNull DeviceMessage message) {
            if (message.getType() == DeviceMessage.Type.VERSION) {
                setFirmware(message.getVersion(), message.getCompileDate());
            }
        }
    };

    public DeviceSession(@NonNull BluetoothHelper bluetoothHelper) {
        this.bluetoothHelper = bluetoothHelper;
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

    /** @return the firmware version, or null if not connected or the device has not answered yet */
    @Nullable
    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    /** @return the firmware compile date, or null if unknown */
    @Nullable
    public String getCompileDate() {
        return compileDate;
    }

    private void setFirmware(@Nullable String version, @Nullable String date) {
        firmwareVersion = version;
        compileDate = date;
        for (Listener listener : listeners) {
            listener.onFirmwareChanged(version, date);
        }
    }
}
