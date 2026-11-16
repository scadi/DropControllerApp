package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class DeviceSimulatorTest {

    @Test
    public void hello_isAnsweredAndMarksConnected() {
        DeviceSimulator simulator = new DeviceSimulator();

        DeviceSimulator.Result result = simulator.feed("[HELLO]");

        assertEquals(Collections.singletonList("[HELLO]"), result.getMessages());
        assertTrue(simulator.isConnected());
        assertEquals(DeviceSimulator.NO_FINISH, result.getFinishDelayMs());
    }

    @Test
    public void bye_marksDisconnectedWithoutReply() {
        DeviceSimulator simulator = new DeviceSimulator();
        simulator.feed("[HELLO]");

        DeviceSimulator.Result result = simulator.feed("[BYE]");

        assertTrue(result.getMessages().isEmpty());
        assertFalse(simulator.isConnected());
    }

    @Test
    public void version_returnsFirmwareVersion() {
        DeviceSimulator simulator = new DeviceSimulator("dC3_TEST", "Feb  2 2026");

        DeviceSimulator.Result result = simulator.feed("[VERSION]");

        assertEquals(Collections.singletonList("[V,dC3_TEST,Feb  2 2026]"), result.getMessages());
    }

    @Test
    public void areYouThere_returnsYia() {
        DeviceSimulator.Result result = new DeviceSimulator().feed("[AYT]");

        assertEquals(Collections.singletonList("[YIA]"), result.getMessages());
    }

    @Test
    public void drainCommands_openAndCloseValves() {
        DeviceSimulator simulator = new DeviceSimulator();

        simulator.feed("[O11]");
        simulator.feed("[O31]");
        assertEquals(new TreeSet<>(Arrays.asList(1, 3)), simulator.getOpenValves());

        simulator.feed("[O10]");
        assertEquals(Collections.singleton(3), simulator.getOpenValves());

        simulator.feed("[OX]");
        assertTrue(simulator.getOpenValves().isEmpty());
    }

    @Test
    public void validSequence_isAcceptedAndFinishesAfterLatestStop() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(2);
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30); // STOP 130
        data.getDrop(1).setStartTime(200);
        data.getDrop(1).setSize(50); // STOP 250

        DeviceSimulator simulator = new DeviceSimulator();
        DeviceSimulator.Result result = feedAll(simulator, ProtocolEncoder.encodeSequence(1, settings, data));

        assertTrue(result.wasSequenceAccepted());
        assertEquals(250, result.getFinishDelayMs());
        // Drops, flashes and camera: the S/N markers are not part of the drop data
        assertEquals(6, simulator.getLastSequence().size());
        assertTrue(simulator.getLastSequence().get(0).startsWith("D01"));
        assertEquals("CN", simulator.getLastSequence().get(5));
        assertEquals("[FTS]", DeviceSimulator.FINISHED_REPLY);
    }

    @Test
    public void enabledFlash_extendsTheSequence() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30); // STOP 130
        data.getFlash(0).setEnabled(true);
        data.getFlash(0).setTime(1000); // STOP 1000 + 50

        DeviceSimulator.Result result = feedAll(new DeviceSimulator(),
                ProtocolEncoder.encodeSequence(1, settings, data));

        assertTrue(result.wasSequenceAccepted());
        assertEquals(1050, result.getFinishDelayMs());
    }

    @Test
    public void bulbCamera_closesAfterTheLastEvent() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30); // STOP 130
        data.getCamera().setEnabled(true);
        data.getCamera().setBulb(true); // STOP 130 + 100

        DeviceSimulator.Result result = feedAll(new DeviceSimulator(),
                ProtocolEncoder.encodeSequence(1, settings, data));

        assertEquals(230, result.getFinishDelayMs());
    }

    @Test
    public void missingFlashTrigger_rejectsTheData() {
        List<String> messages = new ArrayList<>(
                ProtocolEncoder.encodeSequence(1, new AppSettings(), new SequenceData()));
        messages.remove("[F3N]");

        DeviceSimulator.Result result = feedAll(new DeviceSimulator(), messages);

        assertFalse(result.wasSequenceAccepted());
        assertEquals(0, result.getFinishDelayMs());
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void dropCountMismatch_rejectsTheData() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(2);
        List<String> messages = new ArrayList<>(
                ProtocolEncoder.encodeSequence(1, settings, new SequenceData()));
        // Keep [N2] but drop the second D message
        messages.remove(3);

        DeviceSimulator.Result result = feedAll(new DeviceSimulator(), messages);

        assertFalse(result.wasSequenceAccepted());
        assertEquals(0, result.getFinishDelayMs());
    }

    @Test
    public void nextSequence_isAcceptedAfterARejectedOne() {
        DeviceSimulator simulator = new DeviceSimulator();
        List<String> messages = new ArrayList<>(
                ProtocolEncoder.encodeSequence(1, new AppSettings(), new SequenceData()));
        messages.remove("[CN]");
        feedAll(simulator, messages);

        DeviceSimulator.Result result = feedAll(simulator,
                ProtocolEncoder.encodeSequence(2, new AppSettings(), new SequenceData()));

        assertTrue(result.wasSequenceAccepted());
    }

    @Test
    public void messagesSplitAcrossChunks_areFramed() {
        DeviceSimulator simulator = new DeviceSimulator();
        List<String> replies = new ArrayList<>();
        for (byte b : "[HELLO]".getBytes(StandardCharsets.US_ASCII)) {
            replies.addAll(simulator.feed(new byte[]{b}, 0, 1).getMessages());
        }

        assertEquals(Collections.singletonList("[HELLO]"), replies);
    }

    @Test
    public void bytesOutsideBrackets_areIgnored() {
        DeviceSimulator.Result result = new DeviceSimulator().feed("noise[HELLO]more noise");

        assertEquals(Collections.singletonList("[HELLO]"), result.getMessages());
    }

    @Test
    public void unknownMessages_areIgnored() {
        DeviceSimulator.Result result = new DeviceSimulator().feed("[INFO]");

        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    public void reset_forgetsValvesAndConnection() {
        DeviceSimulator simulator = new DeviceSimulator();
        simulator.feed("[HELLO]");
        simulator.feed("[O21]");

        simulator.reset();

        assertFalse(simulator.isConnected());
        assertTrue(simulator.getOpenValves().isEmpty());
    }

    private static DeviceSimulator.Result feedAll(DeviceSimulator simulator, List<String> messages) {
        DeviceSimulator.Result last = simulator.feed("");
        for (String message : messages) {
            last = simulator.feed(message);
        }
        return last;
    }
}
