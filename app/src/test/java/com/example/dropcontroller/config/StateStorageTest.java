package com.example.dropcontroller.config;

import static org.junit.Assert.*;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.data.ThemeMode;

import org.junit.Test;

public class StateStorageTest {

    @Test
    public void loadOnEmptyStore_givesDefaults() {
        StateStorage storage = new StateStorage(new MapStore());
        AppSettings settings = new AppSettings();
        settings.setNumDrops(7);
        SequenceData data = new SequenceData();
        data.getDrop(3).setStartTime(1234);

        storage.loadSettings(settings);
        storage.loadSequence(data);

        assertEquals(new AppSettings(), settings);
        assertEquals(new SequenceData(), data);
    }

    @Test
    public void settings_roundTrip() {
        StateStorage storage = new StateStorage(new MapStore());
        AppSettings saved = new AppSettings();
        saved.setNumDrops(4);
        saved.setLoopModeOn(true);
        saved.setDelayMethod(LoopDelayMethod.BUTTON);
        saved.setWaitSeconds(25);
        saved.setCameraPulseMs(80);
        saved.setFlashPulseMs(120);
        saved.setSoundsOn(false);

        storage.saveSettings(saved);
        AppSettings loaded = new AppSettings();
        storage.loadSettings(loaded);

        assertEquals(saved, loaded);
    }

    @Test
    public void sequence_roundTrip() {
        StateStorage storage = new StateStorage(new MapStore());
        SequenceData saved = new SequenceData();
        saved.getDrop(0).setSolenoid(3);
        saved.getDrop(0).setStartTime(1500);
        saved.getDrop(0).getStartDelta().setDirection(DeltaDirection.DEC);
        saved.getDrop(0).getStartDelta().setAmount(12);
        saved.getDrop(8).setSize(444);
        saved.getDrop(8).getSizeDelta().setAmount(7);
        saved.getCamera().setEnabled(true);
        saved.getCamera().setBulb(true);
        saved.getCamera().setTime(250);
        saved.getCamera().getDelta().setAmount(5);
        saved.getFlash(2).setEnabled(true);
        saved.getFlash(2).setTime(777);
        saved.getFlash(2).getDelta().setDirection(DeltaDirection.DEC);
        saved.getFlash(2).getDelta().setAmount(3);

        storage.saveSequence(saved);
        SequenceData loaded = new SequenceData();
        storage.loadSequence(loaded);

        assertEquals(saved, loaded);
    }

    @Test
    public void themeMode_roundTrip() {
        StateStorage storage = new StateStorage(new MapStore());
        AppSettings saved = new AppSettings();
        saved.setThemeMode(ThemeMode.DARK);

        storage.saveSettings(saved);
        AppSettings loaded = new AppSettings();
        storage.loadSettings(loaded);

        assertEquals(ThemeMode.DARK, loaded.getThemeMode());
    }

    @Test
    public void themeMode_unknownStoredValue_fallsBackToSystem() {
        MapStore store = new MapStore();
        store.storeString("settings.themeMode", "PURPLE");
        StateStorage storage = new StateStorage(store);

        AppSettings settings = new AppSettings();
        settings.setThemeMode(ThemeMode.LIGHT);
        storage.loadSettings(settings);

        assertEquals(ThemeMode.SYSTEM, settings.getThemeMode());
    }

    @Test
    public void invalidStoredValues_areClampedOrDefaulted() {
        MapStore store = new MapStore();
        store.storeInt("settings.numDrops", 50);
        store.storeString("settings.delayMethod", "NOPE");
        store.storeInt("drop.1.start", 123456);
        store.storeString("drop.1.startDelta.dir", "SIDEWAYS");
        StateStorage storage = new StateStorage(store);

        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        storage.loadSettings(settings);
        storage.loadSequence(data);

        assertEquals(9, settings.getNumDrops());
        assertEquals(LoopDelayMethod.TIMER, settings.getDelayMethod());
        assertEquals(9999, data.getDrop(0).getStartTime());
        assertEquals(DeltaDirection.INC, data.getDrop(0).getStartDelta().getDirection());
    }
}
