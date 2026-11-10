package com.example.dropcontroller.logic;

import static org.junit.Assert.*;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Test;

public class SequenceTimingTest {

    @Test
    public void triggerStop_addsPulseTime() {
        assertEquals(1050, SequenceTiming.triggerStop(1000, 50));
    }

    @Test
    public void triggerStop_isCappedToFirmwareLimit() {
        assertEquals(20000, SequenceTiming.triggerStop(19990, 50));
    }

    @Test
    public void cameraStart_bulbStartsAtZero() {
        SequenceData data = new SequenceData();
        data.getCamera().setTime(500);
        data.getCamera().setBulb(true);
        assertEquals(0, SequenceTiming.cameraStart(data.getCamera()));
    }

    @Test
    public void cameraStart_normalModeUsesCameraTime() {
        SequenceData data = new SequenceData();
        data.getCamera().setTime(500);
        assertEquals(500, SequenceTiming.cameraStart(data.getCamera()));
    }

    @Test
    public void latestDropAndFlashStop_ignoresInactiveDropsAndDisabledFlashes() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(1);
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);
        data.getDrop(1).setStartTime(5000);
        data.getFlash(0).setTime(9000);

        assertEquals(130, SequenceTiming.latestDropAndFlashStop(settings, data));
    }

    @Test
    public void latestDropAndFlashStop_includesEnabledFlash() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);
        data.getFlash(1).setEnabled(true);
        data.getFlash(1).setTime(150);

        assertEquals(200, SequenceTiming.latestDropAndFlashStop(settings, data));
    }

    @Test
    public void sequenceEnd_includesCameraStopWhenEnabled() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);
        data.getCamera().setEnabled(true);
        data.getCamera().setTime(1000);

        assertEquals(1050, SequenceTiming.sequenceEnd(settings, data));
    }

    @Test
    public void sequenceEnd_cameraOff_isLatestDropOrFlash() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);
        data.getCamera().setTime(5000);

        assertEquals(130, SequenceTiming.sequenceEnd(settings, data));
    }

    @Test
    public void finishTimeoutMs_sumsSendTimeSequenceEndAndMargin() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);

        // 10 messages * 30 ms + 130 ms sequence end + 5000 ms margin
        assertEquals(5430, SequenceTiming.finishTimeoutMs(settings, data, 10, 30, 5000));
    }

    @Test
    public void finishTimeoutMs_noMessages_isSequenceEndPlusMargin() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);

        assertEquals(1130, SequenceTiming.finishTimeoutMs(settings, data, 0, 30, 1000));
    }
}
