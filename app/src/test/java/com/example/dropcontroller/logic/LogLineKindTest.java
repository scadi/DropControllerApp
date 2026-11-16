package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Checks the classification of communication log lines used for coloring. */
public class LogLineKindTest {

    private static final String TIME = "2026-11-16 10:20:30.123 ";

    @Test
    public void sentLine_isSent_andMessageStartsAfterTimestamp() {
        LogLineKind.Result result = LogLineKind.parse(TIME + "> [HELLO]");

        assertEquals(LogLineKind.SENT, result.getKind());
        assertEquals(TIME.length(), result.getMessageStart());
    }

    @Test
    public void receivedLine_isReceived() {
        assertEquals(LogLineKind.RECEIVED, LogLineKind.parse(TIME + "< [FTS]").getKind());
    }

    @Test
    public void failureEvents_areErrors() {
        assertEquals(LogLineKind.ERROR, LogLineKind.parse(TIME + "# write failed: Broken pipe").getKind());
        assertEquals(LogLineKind.ERROR, LogLineKind.parse(TIME + "# connection failed").getKind());
        assertEquals(LogLineKind.ERROR, LogLineKind.parse(TIME + "# connection lost").getKind());
    }

    @Test
    public void normalEvents_areNotErrors() {
        assertEquals(LogLineKind.EVENT, LogLineKind.parse(TIME + "# connected").getKind());
        assertEquals(LogLineKind.EVENT, LogLineKind.parse(TIME + "# disconnected by the app").getKind());
    }

    @Test
    public void unknownLine_isOther() {
        assertEquals(LogLineKind.OTHER, LogLineKind.parse("garbage").getKind());
        assertEquals(LogLineKind.OTHER, LogLineKind.parse("").getKind());
        assertEquals(LogLineKind.OTHER, LogLineKind.parse(TIME + "text").getKind());
    }
}
