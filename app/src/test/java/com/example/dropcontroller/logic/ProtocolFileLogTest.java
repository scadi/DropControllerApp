package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/** Checks the persistent communication log file. */
public class ProtocolFileLogTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void sentAndReceived_areAppendedToTheFile() throws Exception {
        File file = new File(folder.getRoot(), "logs/protocol.log");
        ProtocolFileLog log = new ProtocolFileLog(file);

        assertTrue(log.sent("[HELLO]"));
        assertTrue(log.received("HELLO"));

        String text = log.readAll();
        assertTrue(text.contains("> [HELLO]"));
        assertTrue(text.contains("< [HELLO]"));
        assertEquals(2, text.split("\n").length);
        assertTrue(file.exists());
    }

    @Test
    public void readAll_returnsEmptyForAMissingFile() throws Exception {
        ProtocolFileLog log = new ProtocolFileLog(new File(folder.getRoot(), "missing/protocol.log"));

        assertEquals("", log.readAll());
    }

    @Test
    public void readAll_returnsNullWhenTheFileCannotBeRead() throws Exception {
        // A directory in place of the file makes the read fail with an IOException
        File directory = folder.newFolder("protocol.log");
        ProtocolFileLog log = new ProtocolFileLog(directory);

        assertNull(log.readAll());
    }

    @Test
    public void clear_emptiesTheFileButKeepsItUsable() throws Exception {
        ProtocolFileLog log = new ProtocolFileLog(new File(folder.getRoot(), "protocol.log"));
        log.sent("[HELLO]");

        assertTrue(log.clear());
        assertEquals("", log.readAll());

        assertTrue(log.sent("[AYT]"));
        assertTrue(log.readAll().contains("[AYT]"));
    }

    @Test
    public void rotation_dropsTheOldestWholeLines() throws Exception {
        File file = new File(folder.getRoot(), "protocol.log");
        ProtocolFileLog log = new ProtocolFileLog(file, 200);

        for (int i = 0; i < 50; i++) {
            assertTrue(log.sent("[MSG" + i + "]"));
        }

        assertTrue("File is too large: " + file.length(), file.length() <= 200);
        String text = log.readAll();
        assertTrue(text.contains("[MSG49]"));
        assertFalse(text.contains("[MSG0]"));
        // Trimming happens at a line break, so every kept line is complete
        assertTrue(text.endsWith("\n"));
    }

    @Test
    public void rotation_trimsWellBelowTheLimit() throws Exception {
        File file = new File(folder.getRoot(), "protocol.log");
        int maxBytes = 200;
        ProtocolFileLog log = new ProtocolFileLog(file, maxBytes);

        long previousLength = 0;
        long smallestLengthAfterTrim = Long.MAX_VALUE;
        for (int i = 0; i < 40; i++) {
            assertTrue(log.sent("[MSG" + i + "]"));
            long length = file.length();
            assertTrue("File is too large: " + length, length <= maxBytes);
            if (length < previousLength) {
                // Hysteresis: a trim goes to about 75% of the limit, so the next message does not trim again
                smallestLengthAfterTrim = Math.min(smallestLengthAfterTrim, length);
            }
            previousLength = length;
        }

        assertTrue("No trim happened", smallestLengthAfterTrim < Long.MAX_VALUE);
        assertTrue("Trimmed only to the limit: " + smallestLengthAfterTrim,
                smallestLengthAfterTrim <= maxBytes * 3 / 4);
    }

    @Test
    public void rotation_keepsTheLastLineWhenItIsLargerThanTheLimit() throws Exception {
        File file = new File(folder.getRoot(), "protocol.log");
        ProtocolFileLog log = new ProtocolFileLog(file, 10);

        assertTrue(log.sent("[A_LONG_MESSAGE]"));

        assertTrue(log.readAll().contains("[A_LONG_MESSAGE]"));
    }

    @Test
    public void maxBytes_isAtLeastOne() throws Exception {
        ProtocolFileLog log = new ProtocolFileLog(new File(folder.getRoot(), "protocol.log"), 0);

        assertEquals(1, log.getMaxBytes());
    }
}
