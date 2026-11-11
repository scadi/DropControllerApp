package com.example.dropcontroller;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.dropcontroller.config.AppPreferences;
import com.example.dropcontroller.config.PresetRepository;
import com.example.dropcontroller.config.StateStorage;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.Preset;
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
    private PresetRepository presetRepository;
    private BluetoothHelper bluetoothHelper;
    private SequenceRunner sequenceRunner;
    private DeviceSession deviceSession;
    private SoundPlayer soundPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        AppPreferences preferences = new AppPreferences(this);
        storage = new StateStorage(preferences);
        presetRepository = new PresetRepository(preferences);
        storage.loadSettings(settings);
        storage.loadSequence(sequence);
        applyThemeMode();
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
    public PresetRepository getPresetRepository() {
        return presetRepository;
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

    /**
     * @return true for debug builds; the device simulator and the debug protocol log are hidden in release builds
     */
    public boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public void saveSettings() {
        storage.saveSettings(settings);
    }

    public void saveSequence() {
        storage.saveSequence(sequence);
    }

    /**
     * Applies the saved theme mode to the whole app. Open activities are recreated when the mode changes.
     */
    public void applyThemeMode() {
        int nightMode;
        switch (settings.getThemeMode()) {
            case LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
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

    /**
     * Loads a preset into the shared settings and drop data (the Sounds switch is kept) and saves them.
     * Refused while a sequence is running.
     *
     * @return true if the preset was applied
     */
    public boolean applyPreset(@NonNull Preset preset) {
        if (sequenceRunner.isActive()) {
            return false;
        }
        settings.copySetupFrom(preset.getSettings());
        sequence.copyFrom(preset.getSequence());
        saveSettings();
        saveSequence();
        return true;
    }
}
