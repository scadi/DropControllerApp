package com.example.dropcontroller;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.dropcontroller.config.AppPreferences;
import com.example.dropcontroller.config.StateStorage;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.services.BluetoothHelper;
import com.example.dropcontroller.services.DeviceSession;
import com.example.dropcontroller.services.SequenceRunner;
import com.example.dropcontroller.services.SoundPlayer;

/**
 * Holds the state that outlives fragments and activities: settings, drop data and the Bluetooth connection.
 * Fragments read and modify the shared objects and call {@link #saveSettings()} / {@link #saveSequence()}.
 */
public class DropControllerApp extends Application {

    private final AppSettings settings = new AppSettings();
    private final SequenceData sequence = new SequenceData();

    private StateStorage storage;
    private BluetoothHelper bluetoothHelper;
    private SequenceRunner sequenceRunner;
    private DeviceSession deviceSession;
    private SoundPlayer soundPlayer;
    @Override
    public void onCreate() {
        super.onCreate();
        storage = new StateStorage(new AppPreferences(this));
        storage.loadSettings(settings);
        storage.loadSequence(sequence);
        bluetoothHelper = new BluetoothHelper(this);
        sequenceRunner = new SequenceRunner(bluetoothHelper, settings, sequence);
        deviceSession = new DeviceSession(bluetoothHelper);
        soundPlayer = new SoundPlayer(settings, sequenceRunner);
    }

    @NonNull
    public AppSettings getSettings() {
        return settings;
    }

    @NonNull
    public SequenceData getSequence() {
        return sequence;
    }

    @NonNull
    public BluetoothHelper getBluetoothHelper() {
        return bluetoothHelper;
    }

    @NonNull
    public SequenceRunner getSequenceRunner() {
        return sequenceRunner;
    }

    @NonNull
    public DeviceSession getDeviceSession() {
        return deviceSession;
    }

    @NonNull
    public SoundPlayer getSoundPlayer() {
        return soundPlayer;
    }

    public void saveSettings() {
        storage.saveSettings(settings);
    }

    public void saveSequence() {
        storage.saveSequence(sequence);
    }

    /**
     * RESET button: restores all settings and drop data to defaults and saves them.
     */
    public void resetAll() {
        sequenceRunner.abort();
        settings.resetToDefaults();
        sequence.resetToDefaults();
        saveSettings();
        saveSequence();
    }
}
