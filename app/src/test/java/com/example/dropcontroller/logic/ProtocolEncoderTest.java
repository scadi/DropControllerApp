package com.example.dropcontroller.logic;

import static org.junit.Assert.*;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Test;

import java.util.List;

public class ProtocolEncoderTest {

    @Test
    public void simpleMessages_areBracketed() {
        assertEquals("[HELLO]", ProtocolEncoder.hello());
        assertEquals("[BYE]", ProtocolEncoder.bye());
        assertEquals("[AYT]", ProtocolEncoder.areYouThere());
        assertEquals("[VERSION]", ProtocolEncoder.version());
        assertEquals("[OX]", ProtocolEncoder.allValvesClose());
    }

    @Test
    public void valveMessages_useValveNumberAndState() {
        assertEquals("[O11]", ProtocolEncoder.valveOpen(1));
        assertEquals("[O60]", ProtocolEncoder.valveClose(6));
    }

    @Test(expected = IllegalArgumentException.class)
    public void valveOpen_outOfRange_throws() {
        ProtocolEncoder.valveOpen(7);
    }

    @Test
    public void pad_zeroPadsAndClamps() {
        assertEquals("0007", ProtocolEncoder.pad(7, 4));
        assertEquals("9999", ProtocolEncoder.pad(123456, 4));
        assertEquals("00000", ProtocolEncoder.pad(-3, 5));
        assertEquals("01", ProtocolEncoder.pad(1, 2));
    }

    @Test
    public void encodeSequence_singleDropAllTriggersOff() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setSolenoid(1);
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);

        List<String> messages = ProtocolEncoder.encodeSequence(1, settings, data);

        assertEquals(8, messages.size());
        assertEquals("[S1]", messages.get(0));
        assertEquals("[N1]", messages.get(1));
        // D + drop no (01) + SOL (1) + START (0100) + STOP (00130)
        assertEquals("[D01" + "1" + "0100" + "00130]", messages.get(2));
        assertEquals("[F1N]", messages.get(3));
        assertEquals("[F2N]", messages.get(4));
        assertEquals("[F3N]", messages.get(5));
        assertEquals("[CN]", messages.get(6));
        assertEquals("[EOD]", messages.get(7));
    }

    @Test
    public void encodeSequence_hasAllMessagesInFirmwareOrder() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(2);
        SequenceData data = new SequenceData();

        List<String> messages = ProtocolEncoder.encodeSequence(3, settings, data);

        assertEquals("[S3]", messages.get(0));
        assertEquals("[N2]", messages.get(1));
        assertTrue(messages.get(2).startsWith("[D01"));
        assertTrue(messages.get(3).startsWith("[D02"));
        assertTrue(messages.get(4).startsWith("[F1"));
        assertTrue(messages.get(5).startsWith("[F2"));
        assertTrue(messages.get(6).startsWith("[F3"));
        assertTrue(messages.get(7).startsWith("[C"));
        assertEquals("[EOD]", messages.get(8));
        assertEquals(9, messages.size());
    }

    @Test
    public void encodeSequence_dropMessage_hasFirmwareLayout() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setSolenoid(6);
        data.getDrop(0).setStartTime(2222);
        data.getDrop(0).setSize(1111);

        String drop = ProtocolEncoder.encodeSequence(1, settings, data).get(2);

        // D + drop no (2) + SOL (1) + START (4) + STOP (5)
        assertEquals("[D0162222" + "03333]", drop);
        assertEquals(15, drop.length());
    }

    @Test
    public void encodeSequence_flashOn_usesTimeAndPulse() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getFlash(0).setEnabled(true);
        data.getFlash(0).setTime(1000);

        List<String> messages = ProtocolEncoder.encodeSequence(1, settings, data);

        assertEquals("[F1Y1000" + "01050]", messages.get(3));
        assertEquals("[F2N]", messages.get(4));
    }

    @Test
    public void encodeSequence_cameraOn_usesTimeAndPulse() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getCamera().setEnabled(true);
        data.getCamera().setTime(1000);

        List<String> messages = ProtocolEncoder.encodeSequence(1, settings, data);

        assertEquals("[CY1000" + "01050]", messages.get(messages.size() - 2));
    }

    @Test
    public void encodeSequence_cameraOff_sendsCN() {
        List<String> messages = ProtocolEncoder.encodeSequence(1, new AppSettings(), new SequenceData());
        assertEquals("[CN]", messages.get(messages.size() - 2));
    }

    @Test
    public void encodeSequence_bulb_opensAtStartAndClosesAfterLastEvent() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(100);
        data.getDrop(0).setSize(30);
        data.getFlash(0).setEnabled(true);
        data.getFlash(0).setTime(150);
        data.getCamera().setEnabled(true);
        data.getCamera().setBulb(true);

        List<String> messages = ProtocolEncoder.encodeSequence(1, settings, data);

        // Latest event is the flash STOP (150 + 50 = 200), plus 100 ms
        assertEquals("[CB0000" + "00300]", messages.get(messages.size() - 2));
    }

    @Test
    public void encodeSequence_triggerStop_isCappedTo20000() {
        AppSettings settings = new AppSettings();
        SequenceData data = new SequenceData();
        data.getCamera().setEnabled(true);
        data.getCamera().setBulb(true);
        data.getDrop(0).setStartTime(9999);
        data.getDrop(0).setSize(9999);

        List<String> messages = ProtocolEncoder.encodeSequence(1, settings, data);

        // The drop STOP itself is not capped by the app, only trigger STOP times
        assertEquals("[D01" + "1" + "9999" + "19998]", messages.get(2));
        assertEquals("[CB0000" + "20000]", messages.get(messages.size() - 2));
    }
}
