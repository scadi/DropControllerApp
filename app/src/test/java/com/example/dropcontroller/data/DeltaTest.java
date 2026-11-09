package com.example.dropcontroller.data;

import static org.junit.Assert.*;

import org.junit.Test;

public class DeltaTest {

    @Test
    public void inc_addsAmount() {
        DeltaValue delta = new DeltaValue(DeltaDirection.INC, 10);
        assertEquals(110, delta.apply(100, 0, 9999));
    }

    @Test
    public void dec_subtractsAmount() {
        DeltaValue delta = new DeltaValue(DeltaDirection.DEC, 10);
        assertEquals(90, delta.apply(100, 0, 9999));
    }

    @Test
    public void dec_clampsToMinimum() {
        DeltaValue delta = new DeltaValue(DeltaDirection.DEC, 50);
        assertEquals(0, delta.apply(20, 0, 9999));
    }

    @Test
    public void inc_clampsToMaximum() {
        DeltaValue delta = new DeltaValue(DeltaDirection.INC, 50);
        assertEquals(9999, delta.apply(9990, 0, 9999));
    }

    @Test
    public void zeroAmount_isInactiveAndChangesNothing() {
        DeltaValue delta = new DeltaValue(DeltaDirection.INC, 0);
        assertFalse(delta.isActive());
        assertEquals(100, delta.apply(100, 0, 9999));
    }

    @Test
    public void amount_isClampedToMaxDelta() {
        DeltaValue delta = new DeltaValue(DeltaDirection.INC, 5000);
        assertEquals(Limits.MAX_DELTA, delta.getAmount());
        delta.setAmount(-5);
        assertEquals(0, delta.getAmount());
    }

    @Test
    public void dropData_applyDeltas_updatesStartAndSize() {
        DropData drop = new DropData();
        drop.setStartTime(100);
        drop.getStartDelta().setDirection(DeltaDirection.INC);
        drop.getStartDelta().setAmount(10);
        drop.setSize(30);
        drop.getSizeDelta().setDirection(DeltaDirection.DEC);
        drop.getSizeDelta().setAmount(5);

        drop.applyDeltas();
        assertEquals(110, drop.getStartTime());
        assertEquals(25, drop.getSize());

        drop.applyDeltas();
        assertEquals(120, drop.getStartTime());
        assertEquals(20, drop.getSize());
    }

    @Test
    public void dropData_stopTime_isStartPlusSize() {
        DropData drop = new DropData();
        drop.setStartTime(1000);
        drop.setSize(70);
        assertEquals(1070, drop.getStopTime());
    }

    @Test
    public void dropData_setters_clampToRange() {
        DropData drop = new DropData();
        drop.setSolenoid(9);
        assertEquals(6, drop.getSolenoid());
        drop.setSolenoid(0);
        assertEquals(1, drop.getSolenoid());
        drop.setStartTime(20000);
        assertEquals(9999, drop.getStartTime());
        drop.setSize(-1);
        assertEquals(0, drop.getSize());
    }

    @Test
    public void sequenceData_applyDeltas_updatesTriggersToo() {
        SequenceData data = new SequenceData();
        data.getFlash(0).setTime(150);
        data.getFlash(0).getDelta().setAmount(10);
        data.getCamera().setTime(5);
        data.getCamera().getDelta().setDirection(DeltaDirection.DEC);
        data.getCamera().getDelta().setAmount(10);

        data.applyDeltas();

        assertEquals(160, data.getFlash(0).getTime());
        assertEquals(0, data.getCamera().getTime());
    }

    @Test
    public void sequenceData_copy_isIndependent() {
        SequenceData original = new SequenceData();
        original.getDrop(0).setStartTime(500);
        SequenceData copy = original.copy();
        assertEquals(original, copy);

        copy.getDrop(0).setStartTime(600);
        assertEquals(500, original.getDrop(0).getStartTime());
        assertNotEquals(original, copy);
    }

    @Test
    public void sequenceData_resetToDefaults_restoresDefaults() {
        SequenceData data = new SequenceData();
        data.getDrop(2).setStartTime(777);
        data.getFlash(1).setEnabled(true);
        data.getCamera().setBulb(true);

        data.resetToDefaults();

        assertEquals(new SequenceData(), data);
    }

    @Test
    public void appSettings_numDrops_isClampedToOneToNine() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(0);
        assertEquals(1, settings.getNumDrops());
        settings.setNumDrops(12);
        assertEquals(9, settings.getNumDrops());
    }

    @Test
    public void appSettings_defaults_haveFiftyMsPulses() {
        AppSettings settings = new AppSettings();
        assertEquals(50, settings.getCameraPulseMs());
        assertEquals(50, settings.getFlashPulseMs());
        assertFalse(settings.isLoopModeOn());
    }

    @Test
    public void appSettings_resetToDefaults_restoresDefaults() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(5);
        settings.setLoopModeOn(true);
        settings.setDelayMethod(LoopDelayMethod.BUTTON);
        settings.setSoundsOn(false);

        settings.resetToDefaults();

        assertEquals(new AppSettings(), settings);
    }
}
