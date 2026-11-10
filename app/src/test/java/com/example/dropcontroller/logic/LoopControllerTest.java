package com.example.dropcontroller.logic;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LoopControllerTest {

    private static class Recorder implements LoopController.Listener {
        final List<Integer> sentSequenceNumbers = new ArrayList<>();
        final List<SequenceData> sentData = new ArrayList<>();
        final List<LoopController.State> states = new ArrayList<>();
        final List<Integer> countdowns = new ArrayList<>();
        int dataChangedCount = 0;
        int countdownFinishedCount = 0;
        int sentCountAtCountdownFinished = -1;

        @Override
        public void onSendSequence(int sequenceNumber, @NonNull SequenceData data) {
            sentSequenceNumbers.add(sequenceNumber);
            sentData.add(data);
        }

        @Override
        public void onCountdownFinished() {
            countdownFinishedCount++;
            sentCountAtCountdownFinished = sentSequenceNumbers.size();
        }

        @Override
        public void onStateChanged(@NonNull LoopController.State state) {
            states.add(state);
        }

        @Override
        public void onCountdown(int secondsRemaining) {
            countdowns.add(secondsRemaining);
        }

        @Override
        public void onDataChanged(int sequenceNumber, @NonNull SequenceData data) {
            dataChangedCount++;
        }
    }

    private AppSettings settings;
    private SequenceData source;
    private Recorder recorder;

    @Before
    public void setUp() {
        settings = new AppSettings();
        settings.setLoopModeOn(true);
        settings.setDelayMethod(LoopDelayMethod.TIMER);
        settings.setWaitSeconds(3);
        source = new SequenceData();
        source.getDrop(0).setStartTime(100);
        source.getDrop(0).getStartDelta().setDirection(DeltaDirection.INC);
        source.getDrop(0).getStartDelta().setAmount(10);
        recorder = new Recorder();
    }

    private LoopController create() {
        return new LoopController(settings, source, recorder);
    }

    @Test
    public void timer_firstSequenceStartsImmediately() {
        LoopController loop = create();

        assertTrue(loop.start());

        assertEquals(LoopController.State.SEQUENCE_ACTIVE, loop.getState());
        assertEquals(1, recorder.sentSequenceNumbers.size());
        assertEquals(1, (int) recorder.sentSequenceNumbers.get(0));
        assertEquals(100, recorder.sentData.get(0).getDrop(0).getStartTime());
    }

    @Test
    public void timer_afterSequence_countdownThenNextSequence() {
        LoopController loop = create();
        loop.start();

        loop.onSequenceFinished();
        assertEquals(LoopController.State.COUNTDOWN, loop.getState());
        assertEquals(3, loop.getSecondsRemaining());

        loop.onSecondTick();
        loop.onSecondTick();
        assertEquals(LoopController.State.COUNTDOWN, loop.getState());
        assertEquals(1, recorder.sentSequenceNumbers.size());

        loop.onSecondTick();
        assertEquals(LoopController.State.SEQUENCE_ACTIVE, loop.getState());
        assertEquals(2, recorder.sentSequenceNumbers.size());
        assertEquals(2, (int) recorder.sentSequenceNumbers.get(1));
    }

    @Test
    public void timer_countdownFinished_isReportedOnceBeforeNextSequence() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();

        loop.onSecondTick();
        loop.onSecondTick();
        assertEquals(0, recorder.countdownFinishedCount);

        loop.onSecondTick();
        assertEquals(1, recorder.countdownFinishedCount);
        // The event comes before the next sequence is sent
        assertEquals(1, recorder.sentCountAtCountdownFinished);
        assertEquals(2, recorder.sentSequenceNumbers.size());
    }

    @Test
    public void countdownFinished_isNotReportedWhenStoppedDuringCountdown() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();
        loop.onSecondTick();

        loop.stop();
        loop.onSecondTick();

        assertEquals(0, recorder.countdownFinishedCount);
    }

    @Test
    public void deltas_areAppliedAfterEachSequence() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();

        assertEquals(110, loop.getData().getDrop(0).getStartTime());

        for (int i = 0; i < 3; i++) {
            loop.onSecondTick();
        }
        assertEquals(110, recorder.sentData.get(1).getDrop(0).getStartTime());

        loop.onSequenceFinished();
        assertEquals(120, loop.getData().getDrop(0).getStartTime());
    }

    @Test
    public void sourceData_isNeverModified() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();

        assertEquals(100, source.getDrop(0).getStartTime());
    }

    @Test
    public void sentData_isACopy() {
        LoopController loop = create();
        loop.start();
        SequenceData sent = recorder.sentData.get(0);

        loop.onSequenceFinished();

        assertEquals(100, sent.getDrop(0).getStartTime());
    }

    @Test
    public void timer_zeroWaitTime_startsNextSequenceAtOnce() {
        settings.setWaitSeconds(0);
        LoopController loop = create();
        loop.start();

        loop.onSequenceFinished();

        assertEquals(LoopController.State.SEQUENCE_ACTIVE, loop.getState());
        assertEquals(2, recorder.sentSequenceNumbers.size());
    }

    @Test
    public void stop_duringCountdown_pausesAtCurrentSequenceNumber() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();
        loop.onSecondTick();

        loop.stop();

        assertEquals(LoopController.State.IDLE, loop.getState());
        assertEquals(2, loop.getSequenceNumber());
        loop.onSecondTick();
        assertEquals(1, recorder.sentSequenceNumbers.size());
    }

    @Test
    public void start_afterStop_continuesWithSameSequenceNumber() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();
        loop.stop();

        assertTrue(loop.start());

        assertEquals(2, recorder.sentSequenceNumbers.size());
        assertEquals(2, (int) recorder.sentSequenceNumbers.get(1));
        assertEquals(110, recorder.sentData.get(1).getDrop(0).getStartTime());
    }

    @Test
    public void stop_whileSequenceActive_pausesWhenItFinishes() {
        LoopController loop = create();
        loop.start();

        loop.stop();
        assertEquals(LoopController.State.SEQUENCE_ACTIVE, loop.getState());

        loop.onSequenceFinished();
        assertEquals(LoopController.State.IDLE, loop.getState());
        assertEquals(2, loop.getSequenceNumber());
    }

    @Test
    public void reset_setsSequenceToOneAndRestoresStartValues() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();
        loop.stop();
        assertEquals(110, loop.getData().getDrop(0).getStartTime());

        assertTrue(loop.reset());

        assertEquals(1, loop.getSequenceNumber());
        assertEquals(100, loop.getData().getDrop(0).getStartTime());
        assertEquals(LoopController.State.IDLE, loop.getState());
    }

    @Test
    public void reset_whileSequenceActive_isIgnored() {
        LoopController loop = create();
        loop.start();

        assertFalse(loop.reset());

        assertEquals(LoopController.State.SEQUENCE_ACTIVE, loop.getState());
    }

    @Test
    public void button_waitsForStartAfterEachSequence() {
        settings.setDelayMethod(LoopDelayMethod.BUTTON);
        LoopController loop = create();
        loop.start();

        loop.onSequenceFinished();
        assertEquals(LoopController.State.WAITING_FOR_BUTTON, loop.getState());
        loop.onSecondTick();
        loop.onSecondTick();
        assertEquals(1, recorder.sentSequenceNumbers.size());

        assertTrue(loop.start());
        assertEquals(2, recorder.sentSequenceNumbers.size());
        assertEquals(110, recorder.sentData.get(1).getDrop(0).getStartTime());
    }

    @Test
    public void start_whileSequenceActive_isIgnored() {
        LoopController loop = create();
        loop.start();

        assertFalse(loop.start());

        assertEquals(1, recorder.sentSequenceNumbers.size());
    }

    @Test
    public void onSequenceFinished_whenNotActive_isIgnored() {
        LoopController loop = create();

        loop.onSequenceFinished();

        assertEquals(LoopController.State.IDLE, loop.getState());
        assertEquals(1, loop.getSequenceNumber());
    }

    @Test
    public void connectionLost_stopsAtCurrentSequenceNumber() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();

        loop.onConnectionLost();

        assertEquals(LoopController.State.IDLE, loop.getState());
        assertEquals(2, loop.getSequenceNumber());
    }

    @Test
    public void deltas_neverLeaveTheValidRange() {
        source.getDrop(0).setStartTime(9995);
        LoopController loop = create();
        loop.start();

        loop.onSequenceFinished();

        assertEquals(9999, loop.getData().getDrop(0).getStartTime());
    }

    @Test
    public void countdown_isReportedEverySecond() {
        LoopController loop = create();
        loop.start();
        loop.onSequenceFinished();
        loop.onSecondTick();
        loop.onSecondTick();

        assertEquals(3, recorder.countdowns.size());
        assertEquals(3, (int) recorder.countdowns.get(0));
        assertEquals(2, (int) recorder.countdowns.get(1));
        assertEquals(1, (int) recorder.countdowns.get(2));
    }
}
