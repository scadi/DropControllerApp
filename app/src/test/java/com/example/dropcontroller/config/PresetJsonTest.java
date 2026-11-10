package com.example.dropcontroller.config;

import static org.junit.Assert.*;

import com.example.dropcontroller.config.PresetJson.FormatException;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.Preset;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PresetJsonTest {

    private static Preset sample(int id, String name) {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(4);
        settings.setLoopModeOn(true);
        settings.setDelayMethod(LoopDelayMethod.BUTTON);
        settings.setWaitSeconds(33);
        settings.setCameraPulseMs(60);
        settings.setFlashPulseMs(80);
        SequenceData data = new SequenceData();
        data.getDrop(0).setSolenoid(5);
        data.getDrop(0).setStartTime(250);
        data.getDrop(0).getStartDelta().setDirection(DeltaDirection.DEC);
        data.getDrop(0).getStartDelta().setAmount(7);
        data.getDrop(8).setSize(999);
        data.getDrop(8).getSizeDelta().setAmount(11);
        data.getCamera().setEnabled(true);
        data.getCamera().setBulb(true);
        data.getCamera().setTime(300);
        data.getCamera().getDelta().setAmount(2);
        data.getFlash(2).setEnabled(true);
        data.getFlash(2).setTime(444);
        data.getFlash(2).getDelta().setDirection(DeltaDirection.DEC);
        data.getFlash(2).getDelta().setAmount(4);
        return new Preset(id, name, settings, data);
    }

    private static String file(String presetsJson) {
        return "{\"format\":\"dropcontroller-presets\",\"version\":1,\"presets\":" + presetsJson + "}";
    }

    @Test
    public void roundTrip_keepsNamesAndContentInOrder() throws FormatException {
        List<Preset> original = new ArrayList<>();
        original.add(sample(1, "Crown"));
        original.add(new Preset(2, "Empty \"quoted\" \u00e9", new AppSettings(), new SequenceData()));

        List<Preset> read = PresetJson.read(PresetJson.write(original));

        assertEquals(2, read.size());
        assertEquals("Crown", read.get(0).getName());
        assertTrue(read.get(0).hasSameContentAs(original.get(0)));
        assertEquals("Empty \"quoted\" \u00e9", read.get(1).getName());
        assertTrue(read.get(1).hasSameContentAs(original.get(1)));
        assertEquals(Preset.NO_ID, read.get(0).getId());
    }

    @Test
    public void write_hasFormatMarkerAndVersion() {
        String text = PresetJson.write(new ArrayList<>());

        assertTrue(text.contains("\"format\": \"dropcontroller-presets\""));
        assertTrue(text.contains("\"version\": 1"));
    }

    @Test
    public void write_doesNotContainSounds() {
        assertFalse(PresetJson.write(java.util.Collections.singletonList(sample(1, "A"))).toLowerCase()
                .contains("sound"));
    }

    @Test
    public void emptyList_isValid() throws FormatException {
        assertTrue(PresetJson.read(file("[]")).isEmpty());
    }

    @Test
    public void bom_isAccepted() throws FormatException {
        assertEquals(1, PresetJson.read("\uFEFF" + file("[{\"name\":\"A\"}]")).size());
    }

    @Test(expected = FormatException.class)
    public void notJson_isRejected() throws FormatException {
        PresetJson.read("this is not json");
    }

    @Test(expected = FormatException.class)
    public void emptyText_isRejected() throws FormatException {
        PresetJson.read("");
    }

    @Test(expected = FormatException.class)
    public void wrongFormatMarker_isRejected() throws FormatException {
        PresetJson.read("{\"format\":\"other\",\"version\":1,\"presets\":[]}");
    }

    @Test(expected = FormatException.class)
    public void missingFormatMarker_isRejected() throws FormatException {
        PresetJson.read("{\"version\":1,\"presets\":[]}");
    }

    @Test(expected = FormatException.class)
    public void newerVersion_isRejected() throws FormatException {
        PresetJson.read("{\"format\":\"dropcontroller-presets\",\"version\":2,\"presets\":[]}");
    }

    @Test(expected = FormatException.class)
    public void missingVersion_isRejected() throws FormatException {
        PresetJson.read("{\"format\":\"dropcontroller-presets\",\"presets\":[]}");
    }

    @Test(expected = FormatException.class)
    public void missingPresetsList_isRejected() throws FormatException {
        PresetJson.read("{\"format\":\"dropcontroller-presets\",\"version\":1}");
    }

    @Test(expected = FormatException.class)
    public void presetsNotAList_isRejected() throws FormatException {
        PresetJson.read("{\"format\":\"dropcontroller-presets\",\"version\":1,\"presets\":{}}");
    }

    @Test
    public void missingFields_useDefaults() throws FormatException {
        List<Preset> read = PresetJson.read(file("[{\"name\":\"Bare\"}]"));

        assertEquals(1, read.size());
        assertTrue(read.get(0).hasSameContentAs(new AppSettings(), new SequenceData()));
    }

    @Test
    public void partialDrops_keepDefaultsForTheRest() throws FormatException {
        List<Preset> read = PresetJson.read(file(
                "[{\"name\":\"P\",\"drops\":[{\"start\":777}],\"flashes\":[{\"enabled\":true}]}]"));

        SequenceData sequence = read.get(0).getSequence();
        assertEquals(777, sequence.getDrop(0).getStartTime());
        assertEquals(new SequenceData().getDrop(0).getSize(), sequence.getDrop(0).getSize());
        assertEquals(new SequenceData().getDrop(1), sequence.getDrop(1));
        assertTrue(sequence.getFlash(0).isEnabled());
        assertEquals(new SequenceData().getFlash(0).getTime(), sequence.getFlash(0).getTime());
    }

    @Test
    public void outOfRangeAndUnknownValues_areClampedOrDefaulted() throws FormatException {
        List<Preset> read = PresetJson.read(file("[{\"name\":\"X\","
                + "\"settings\":{\"numDrops\":50,\"delayMethod\":\"NOPE\",\"waitSeconds\":-5},"
                + "\"drops\":[{\"solenoid\":99,\"start\":123456,\"startDelta\":{\"direction\":\"SIDEWAYS\","
                + "\"amount\":5000}}]}]"));

        Preset preset = read.get(0);
        assertEquals(9, preset.getSettings().getNumDrops());
        assertEquals(LoopDelayMethod.TIMER, preset.getSettings().getDelayMethod());
        assertEquals(0, preset.getSettings().getWaitSeconds());
        assertEquals(6, preset.getSequence().getDrop(0).getSolenoid());
        assertEquals(9999, preset.getSequence().getDrop(0).getStartTime());
        assertEquals(DeltaDirection.INC, preset.getSequence().getDrop(0).getStartDelta().getDirection());
        assertEquals(999, preset.getSequence().getDrop(0).getStartDelta().getAmount());
    }

    @Test
    public void extraDrops_areIgnored() throws FormatException {
        StringBuilder drops = new StringBuilder("[");
        for (int i = 0; i < 12; i++) {
            drops.append(i == 0 ? "" : ",").append("{\"start\":").append(100 + i).append('}');
        }
        drops.append(']');

        List<Preset> read = PresetJson.read(file("[{\"name\":\"Many\",\"drops\":" + drops + "}]"));

        assertEquals(108, read.get(0).getSequence().getDrop(8).getStartTime());
    }

    @Test
    public void entriesWithoutUsableName_areSkipped() throws FormatException {
        List<Preset> read = PresetJson.read(file(
                "[{\"name\":\"  \"},{\"x\":1},\"text\",42,null,{\"name\":\"Ok\"}]"));

        assertEquals(1, read.size());
        assertEquals("Ok", read.get(0).getName());
    }

    @Test
    public void names_areTrimmedAndShortened() throws FormatException {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            longName.append('n');
        }

        List<Preset> read = PresetJson.read(file(
                "[{\"name\":\"  Spaced  \"},{\"name\":\"" + longName + "\"}]"));

        assertEquals("Spaced", read.get(0).getName());
        assertEquals(PresetRepository.MAX_NAME_LENGTH, read.get(1).getName().length());
    }
}
