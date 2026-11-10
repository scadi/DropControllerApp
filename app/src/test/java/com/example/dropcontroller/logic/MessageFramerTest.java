package com.example.dropcontroller.logic;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MessageFramerTest {

    private static List<String> feed(MessageFramer framer, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return framer.feed(bytes, 0, bytes.length);
    }

    @Test
    public void feed_singleMessage_returnsPayloadWithoutBrackets() {
        List<String> messages = feed(new MessageFramer(), "[HELLO]");
        assertEquals(1, messages.size());
        assertEquals("HELLO", messages.get(0));
    }

    @Test
    public void feed_messageSplitAcrossReads_isAssembled() {
        MessageFramer framer = new MessageFramer();
        assertTrue(feed(framer, "[V,dC3_").isEmpty());
        assertTrue(feed(framer, "AS25006,Jan").isEmpty());
        List<String> messages = feed(framer, "  1 2025]");
        assertEquals(1, messages.size());
        assertEquals("V,dC3_AS25006,Jan  1 2025", messages.get(0));
    }

    @Test
    public void feed_severalMessagesInOneRead_areAllReturned() {
        List<String> messages = feed(new MessageFramer(), "[YIA][FTS]");
        assertEquals(2, messages.size());
        assertEquals("YIA", messages.get(0));
        assertEquals("FTS", messages.get(1));
    }

    @Test
    public void feed_bytesOutsideBrackets_areIgnored() {
        List<String> messages = feed(new MessageFramer(), "junk\r\n[FTS]more");
        assertEquals(1, messages.size());
        assertEquals("FTS", messages.get(0));
    }

    @Test
    public void feed_newStartMarker_discardsIncompleteMessage() {
        List<String> messages = feed(new MessageFramer(), "[HEL[FTS]");
        assertEquals(1, messages.size());
        assertEquals("FTS", messages.get(0));
    }

    @Test
    public void feed_tooLongMessage_isDropped() {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            text.append('A');
        }
        text.append("][FTS]");
        List<String> messages = feed(new MessageFramer(), text.toString());
        assertEquals(1, messages.size());
        assertEquals("FTS", messages.get(0));
    }

    @Test
    public void feed_usesOffsetAndLength() {
        byte[] bytes = "xx[YIA]yy".getBytes(StandardCharsets.US_ASCII);
        List<String> messages = new MessageFramer().feed(bytes, 2, 5);
        assertEquals(1, messages.size());
        assertEquals("YIA", messages.get(0));
    }

    @Test
    public void reset_discardsPartialMessage() {
        MessageFramer framer = new MessageFramer();
        feed(framer, "[HEL");
        framer.reset();
        assertTrue(feed(framer, "LO]").isEmpty());
    }

    @Test
    public void deviceMessage_parse_knownMessages() {
        assertEquals(DeviceMessage.Type.HELLO, DeviceMessage.parse("HELLO").getType());
        assertEquals(DeviceMessage.Type.YES_I_AM, DeviceMessage.parse("YIA").getType());
        assertEquals(DeviceMessage.Type.FINISHED, DeviceMessage.parse("FTS").getType());
        assertEquals(DeviceMessage.Type.UNKNOWN, DeviceMessage.parse("???").getType());
    }

    @Test
    public void deviceMessage_parse_version() {
        DeviceMessage message = DeviceMessage.parse("V,dC3_AS25006,Jan  1 2025");
        assertEquals(DeviceMessage.Type.VERSION, message.getType());
        assertEquals("dC3_AS25006", message.getVersion());
        assertEquals("Jan  1 2025", message.getCompileDate());
    }

    @Test
    public void deviceMessage_parse_versionWithoutDate() {
        DeviceMessage message = DeviceMessage.parse("V,dC3_AS25006");
        assertEquals("dC3_AS25006", message.getVersion());
        assertEquals("", message.getCompileDate());
    }

    @Test
    public void deviceMessage_parse_popupMessage() {
        DeviceMessage message = DeviceMessage.parse("M,Title,Some text, with comma");
        assertEquals(DeviceMessage.Type.MESSAGE, message.getType());
        assertEquals("Title", message.getTitle());
        assertEquals("Some text, with comma", message.getMessage());
    }
}
