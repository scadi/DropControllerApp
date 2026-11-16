package com.example.dropcontroller.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.logic.DeviceSimulator;
import com.example.dropcontroller.logic.ProtocolEncoder;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Checks the in-process link itself: the pipes, the reader thread and the scheduled [FTS]. The protocol details
 * are covered by {@code DeviceSimulatorTest}.
 */
public class SimulatorLinkTest {

    private static final int TIMEOUT_MS = 5000;

    @Test(timeout = TIMEOUT_MS)
    public void hello_isAnsweredThroughThePipes() throws IOException {
        SimulatorLink link = new SimulatorLink(new DeviceSimulator());
        try {
            write(link, "[HELLO]");

            assertEquals("[HELLO]", readMessage(link.getInputStream()));
        } finally {
            link.close();
        }
    }

    @Test(timeout = TIMEOUT_MS)
    public void version_isAnsweredThroughThePipes() throws IOException {
        SimulatorLink link = new SimulatorLink(new DeviceSimulator());
        try {
            write(link, "[VERSION]");

            assertEquals("[V,dC3_AS25006,Jan  1 2025]", readMessage(link.getInputStream()));
        } finally {
            link.close();
        }
    }

    @Test(timeout = TIMEOUT_MS)
    public void finishedFlag_isSentAfterTheSequenceTime() throws IOException {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(0);
        data.getDrop(0).setSize(200); // STOP 200 ms
        List<String> messages = ProtocolEncoder.encodeSequence(1, settings, data);

        SimulatorLink link = new SimulatorLink(new DeviceSimulator());
        try {
            long start = System.currentTimeMillis();
            for (String message : messages) {
                write(link, message);
            }

            assertEquals("[FTS]", readMessage(link.getInputStream()));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue("FTS arrived too early: " + elapsed + " ms", elapsed >= 150);
        } finally {
            link.close();
        }
    }

    @Test(timeout = TIMEOUT_MS)
    public void rejectedSequence_finishesWithoutWaiting() throws IOException {
        List<String> messages = ProtocolEncoder.encodeSequence(1, new AppSettings(), new SequenceData());
        messages.remove("[CN]");

        SimulatorLink link = new SimulatorLink(new DeviceSimulator());
        try {
            for (String message : messages) {
                write(link, message);
            }

            assertEquals("[FTS]", readMessage(link.getInputStream()));
        } finally {
            link.close();
        }
    }

    @Test(timeout = TIMEOUT_MS)
    public void newSequence_cancelsThePendingFinishedFlag() throws Exception {
        SimulatorLink link = new SimulatorLink(new DeviceSimulator());
        try {
            for (String message : sequenceWithStop(600)) {
                write(link, message);
            }

            long start = System.currentTimeMillis();
            for (String message : sequenceWithStop(100)) {
                write(link, message);
            }
            assertEquals("[FTS]", readMessage(link.getInputStream()));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue("The old [FTS] arrived instead of the new one: " + elapsed + " ms", elapsed < 500);

            // Wait past the finish time of the first sequence: its cancelled [FTS] must not arrive as well
            Thread.sleep(700);
            assertEquals("A cancelled [FTS] was sent", 0, link.getInputStream().available());
        } finally {
            link.close();
        }
    }

    private static List<String> sequenceWithStop(int stopMs) {
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(0);
        data.getDrop(0).setSize(stopMs);
        return ProtocolEncoder.encodeSequence(1, new AppSettings(), data);
    }

    private static void write(SimulatorLink link, String message) throws IOException {
        OutputStream out = link.getOutputStream();
        out.write(message.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static String readMessage(InputStream in) throws IOException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        StringBuilder payload = new StringBuilder();
        boolean inMessage = false;
        while (System.currentTimeMillis() < deadline) {
            if (in.available() <= 0) {
                sleepBriefly();
                continue;
            }
            int value = in.read();
            if (value < 0) {
                throw new IOException("The simulator closed the stream");
            }
            char c = (char) value;
            if (c == '[') {
                payload.setLength(0);
                inMessage = true;
            } else if (!inMessage) {
                continue;
            } else if (c == ']') {
                return "[" + payload + "]";
            } else {
                payload.append(c);
            }
        }
        throw new IOException("Timed out waiting for a message");
    }

    private static void sleepBriefly() throws IOException {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a message", e);
        }
    }
}
