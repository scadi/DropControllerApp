package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks the in-memory protocol log that the debug dialog shows. */
public class ProtocolLogTest {

    @Test
    public void sentAndReceived_areRecordedInOrder() {
        ProtocolLog log = new ProtocolLog();

        log.sent("[HELLO]");
        log.received("HELLO");

        List<ProtocolLog.Entry> entries = log.getEntries();
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).isSent());
        assertEquals("[HELLO]", entries.get(0).getMessage());
        assertFalse(entries.get(1).isSent());
        assertEquals(ProtocolLog.Direction.RECEIVED, entries.get(1).getDirection());
    }

    @Test
    public void received_addsTheBracketsBack() {
        ProtocolLog log = new ProtocolLog();

        log.received("V,dC3_AS25006,Jan  1 2025");

        assertEquals("[V,dC3_AS25006,Jan  1 2025]", log.getEntries().get(0).getMessage());
    }

    @Test
    public void getEntries_isASnapshotThatDoesNotChange() {
        ProtocolLog log = new ProtocolLog();
        log.sent("[AYT]");

        List<ProtocolLog.Entry> snapshot = log.getEntries();
        log.sent("[CN]");

        assertEquals(1, snapshot.size());
        assertEquals(2, log.getEntries().size());
    }

    @Test
    public void capacity_keepsTheNewestEntries() {
        ProtocolLog log = new ProtocolLog(3);

        log.sent("[1]");
        log.sent("[2]");
        log.sent("[3]");
        log.sent("[4]");

        List<ProtocolLog.Entry> entries = log.getEntries();
        assertEquals(3, entries.size());
        assertEquals("[2]", entries.get(0).getMessage());
        assertEquals("[4]", entries.get(2).getMessage());
    }

    @Test
    public void clear_removesAllEntries() {
        ProtocolLog log = new ProtocolLog();
        log.sent("[HELLO]");

        log.clear();

        assertTrue(log.getEntries().isEmpty());
    }

    @Test
    public void listeners_areNotifiedOnNewEntriesAndClear() {
        ProtocolLog log = new ProtocolLog();
        AtomicInteger notifications = new AtomicInteger();
        ProtocolLog.Listener listener = notifications::incrementAndGet;

        log.addListener(listener);
        log.sent("[HELLO]");
        log.received("HELLO");
        log.clear();

        assertEquals(3, notifications.get());
    }

    @Test
    public void removedListener_isNotNotified() {
        ProtocolLog log = new ProtocolLog();
        AtomicInteger notifications = new AtomicInteger();
        ProtocolLog.Listener listener = notifications::incrementAndGet;

        log.addListener(listener);
        log.removeListener(listener);
        log.sent("[HELLO]");

        assertEquals(0, notifications.get());
    }

    @Test
    public void entries_haveATimestamp() {
        ProtocolLog log = new ProtocolLog();
        long before = System.currentTimeMillis();

        log.sent("[HELLO]");

        assertTrue(log.getEntries().get(0).getTimestampMs() >= before);
    }

    @Test
    public void throwingListener_doesNotBreakTheLogOrOtherListeners() {
        ProtocolLog log = new ProtocolLog();
        AtomicInteger notifications = new AtomicInteger();
        log.addListener(() -> {
            throw new IllegalStateException("broken listener");
        });
        log.addListener(notifications::incrementAndGet);

        log.sent("[HELLO]");

        assertEquals(1, notifications.get());
        assertEquals(1, log.getEntries().size());
    }
}
