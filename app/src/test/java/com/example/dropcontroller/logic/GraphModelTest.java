package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class GraphModelTest {

    private AppSettings settings;
    private SequenceData data;

    @Before
    public void setUp() {
        settings = new AppSettings();
        data = new SequenceData();
    }

    @Test
    public void build_oneBarPerActiveDrop() {
        settings.setNumDrops(3);

        List<GraphModel.Bar> bars = GraphModel.build(settings, data).getBars();

        assertEquals(3, bars.size());
        assertEquals(GraphModel.BarType.DROP, bars.get(2).getType());
        assertEquals(3, bars.get(2).getIndex());
    }

    @Test
    public void build_dropBarRunsFromStartToStartPlusSize() {
        data.getDrop(0).setStartTime(200);
        data.getDrop(0).setSize(35);

        GraphModel.Bar bar = GraphModel.build(settings, data).getBars().get(0);

        assertEquals(200, bar.getStartMs());
        assertEquals(235, bar.getStopMs());
    }

    @Test
    public void build_disabledTriggersHaveNoBars() {
        assertEquals(1, GraphModel.build(settings, data).getBars().size());
    }

    @Test
    public void build_enabledFlashUsesTimeAndPulse() {
        data.getFlash(1).setEnabled(true);
        data.getFlash(1).setTime(400);
        settings.setFlashPulseMs(50);

        List<GraphModel.Bar> bars = GraphModel.build(settings, data).getBars();

        GraphModel.Bar flash = bars.get(bars.size() - 1);
        assertEquals(GraphModel.BarType.FLASH, flash.getType());
        assertEquals(2, flash.getIndex());
        assertEquals(400, flash.getStartMs());
        assertEquals(450, flash.getStopMs());
    }

    @Test
    public void build_cameraShutterOpenForTheExposureDuration() {
        data.getCamera().setEnabled(true);
        data.getCamera().setTime(300);
        settings.setCameraPulseMs(80);

        List<GraphModel.Bar> bars = GraphModel.build(settings, data).getBars();

        GraphModel.Bar camera = bars.get(bars.size() - 1);
        assertEquals(GraphModel.BarType.CAMERA, camera.getType());
        assertEquals(300, camera.getStartMs());
        assertEquals(380, camera.getStopMs());
    }

    @Test
    public void build_bulbCameraCoversTheWholeSequence() {
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(50);
        data.getCamera().setEnabled(true);
        data.getCamera().setBulb(true);

        List<GraphModel.Bar> bars = GraphModel.build(settings, data).getBars();

        GraphModel.Bar camera = bars.get(bars.size() - 1);
        assertEquals(0, camera.getStartMs());
        assertEquals(150 + SequenceTiming.BULB_POST_MS, camera.getStopMs());
    }

    @Test
    public void build_axisEndsAtAWholeTickAfterTheLastBar() {
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(237);

        GraphModel model = GraphModel.build(settings, data);

        assertTrue(model.getAxisMaxMs() >= 337);
        assertEquals(0, model.getAxisMaxMs() % model.getTickStepMs());
    }

    @Test
    public void build_axisHasAMinimumLength() {
        data.getDrop(0).setStartTime(0);
        data.getDrop(0).setSize(0);

        assertEquals(100, GraphModel.build(settings, data).getAxisMaxMs());
    }

    @Test
    public void tickStepFor_givesAtMostAboutTenTicks() {
        assertEquals(10, GraphModel.tickStepFor(100));
        assertEquals(50, GraphModel.tickStepFor(500));
        assertEquals(100, GraphModel.tickStepFor(1000));
        assertEquals(2000, GraphModel.tickStepFor(20000));
        assertEquals(200, GraphModel.tickStepFor(1500));
    }
}
