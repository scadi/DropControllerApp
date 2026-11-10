package com.example.dropcontroller.config;

import static org.junit.Assert.*;

import com.example.dropcontroller.config.PresetRepository.NameCheck;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.Preset;
import com.example.dropcontroller.data.SequenceData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PresetRepositoryTest {

    private final MapStore store = new MapStore();
    private final PresetRepository repository = new PresetRepository(store);

    private static AppSettings customSettings() {
        AppSettings settings = new AppSettings();
        settings.setNumDrops(3);
        settings.setLoopModeOn(true);
        settings.setDelayMethod(LoopDelayMethod.BUTTON);
        settings.setWaitSeconds(42);
        settings.setCameraPulseMs(70);
        settings.setFlashPulseMs(90);
        return settings;
    }

    private static SequenceData customSequence() {
        SequenceData data = new SequenceData();
        data.getDrop(0).setSolenoid(4);
        data.getDrop(0).setStartTime(321);
        data.getDrop(0).getStartDelta().setDirection(DeltaDirection.DEC);
        data.getDrop(0).getStartDelta().setAmount(9);
        data.getDrop(8).setSize(555);
        data.getCamera().setEnabled(true);
        data.getCamera().setBulb(true);
        data.getFlash(1).setEnabled(true);
        data.getFlash(1).setTime(88);
        return data;
    }

    @Test
    public void emptyRepository_hasNoPresets() {
        assertTrue(repository.getAll().isEmpty());
        assertNull(repository.findByName("x"));
        assertNull(repository.findById(1));
    }

    @Test
    public void add_thenLoad_roundTrip() {
        AppSettings settings = customSettings();
        SequenceData sequence = customSequence();

        Preset added = repository.add("  Crown  ", settings, sequence);

        assertEquals("Crown", added.getName());
        Preset loaded = repository.findById(added.getId());
        assertNotNull(loaded);
        assertEquals("Crown", loaded.getName());
        assertTrue(loaded.getSettings().hasSameSetupAs(settings));
        assertEquals(sequence, loaded.getSequence());
        assertTrue(loaded.hasSameContentAs(settings, sequence));
    }

    @Test
    public void add_copiesTheData() {
        AppSettings settings = customSettings();
        SequenceData sequence = customSequence();
        Preset added = repository.add("A", settings, sequence);

        sequence.getDrop(0).setStartTime(1);
        settings.setNumDrops(9);

        assertEquals(321, added.getSequence().getDrop(0).getStartTime());
        assertEquals(3, added.getSettings().getNumDrops());
    }

    @Test
    public void soundsSwitch_isNotPartOfAPreset() {
        AppSettings settings = customSettings();
        settings.setSoundsOn(false);
        Preset preset = repository.add("Quiet", settings, customSequence());

        assertTrue(repository.findById(preset.getId()).getSettings().isSoundsOn());

        AppSettings current = new AppSettings();
        current.setSoundsOn(false);
        current.copySetupFrom(preset.getSettings());
        assertFalse(current.isSoundsOn());
        assertEquals(3, current.getNumDrops());
    }

    @Test
    public void presets_areListedInCreationOrder() {
        repository.add("B", new AppSettings(), new SequenceData());
        repository.add("A", new AppSettings(), new SequenceData());
        repository.add("C", new AppSettings(), new SequenceData());

        List<Preset> all = repository.getAll();

        assertEquals(3, all.size());
        assertEquals("B", all.get(0).getName());
        assertEquals("A", all.get(1).getName());
        assertEquals("C", all.get(2).getName());
    }

    @Test
    public void findByName_ignoresCaseAndSpaces() {
        Preset added = repository.add("Crown", new AppSettings(), new SequenceData());

        Preset found = repository.findByName("  cROWN ");

        assertNotNull(found);
        assertEquals(added.getId(), found.getId());
    }

    @Test
    public void checkName_rejectsEmptyTooLongAndDuplicate() {
        Preset added = repository.add("Crown", new AppSettings(), new SequenceData());

        assertEquals(NameCheck.EMPTY, repository.checkName("   ", -1));
        assertEquals(NameCheck.TOO_LONG, repository.checkName(repeat('x', 41), -1));
        assertEquals(NameCheck.OK, repository.checkName(repeat('x', 40), -1));
        assertEquals(NameCheck.DUPLICATE, repository.checkName("CROWN", -1));
        assertEquals(NameCheck.OK, repository.checkName("CROWN", added.getId()));
    }

    @Test
    public void nameLength_isCountedAfterTrimming() {
        assertEquals(NameCheck.OK, repository.checkName("  " + repeat('x', 40) + "  ", -1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_duplicateName_throws() {
        repository.add("Crown", new AppSettings(), new SequenceData());
        repository.add("crown", new AppSettings(), new SequenceData());
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_emptyName_throws() {
        repository.add(" ", new AppSettings(), new SequenceData());
    }

    @Test
    public void overwrite_replacesContentAndKeepsIdAndPosition() {
        Preset first = repository.add("First", new AppSettings(), new SequenceData());
        repository.add("Second", new AppSettings(), new SequenceData());

        AppSettings settings = customSettings();
        SequenceData sequence = customSequence();
        Preset updated = repository.overwrite(first.getId(), "First", settings, sequence);

        assertNotNull(updated);
        assertEquals(first.getId(), updated.getId());
        List<Preset> all = repository.getAll();
        assertEquals(2, all.size());
        assertEquals("First", all.get(0).getName());
        assertTrue(all.get(0).hasSameContentAs(settings, sequence));
        assertTrue(all.get(1).hasSameContentAs(new AppSettings(), new SequenceData()));
    }

    @Test
    public void overwrite_unknownId_returnsNull() {
        assertNull(repository.overwrite(99, "X", new AppSettings(), new SequenceData()));
        assertTrue(repository.getAll().isEmpty());
    }

    @Test
    public void rename_changesOnlyTheName() {
        Preset preset = repository.add("Old", customSettings(), customSequence());

        assertEquals(NameCheck.OK, repository.rename(preset.getId(), " New "));

        Preset loaded = repository.findById(preset.getId());
        assertEquals("New", loaded.getName());
        assertTrue(loaded.hasSameContentAs(customSettings(), customSequence()));
        assertNull(repository.findByName("Old"));
    }

    @Test
    public void rename_toOwnNameWithOtherCase_isAllowed() {
        Preset preset = repository.add("crown", new AppSettings(), new SequenceData());

        assertEquals(NameCheck.OK, repository.rename(preset.getId(), "Crown"));

        assertEquals("Crown", repository.findById(preset.getId()).getName());
    }

    @Test
    public void rename_rejectsDuplicateEmptyAndTooLong_andChangesNothing() {
        repository.add("A", new AppSettings(), new SequenceData());
        Preset b = repository.add("B", new AppSettings(), new SequenceData());

        assertEquals(NameCheck.DUPLICATE, repository.rename(b.getId(), "a"));
        assertEquals(NameCheck.EMPTY, repository.rename(b.getId(), ""));
        assertEquals(NameCheck.TOO_LONG, repository.rename(b.getId(), repeat('y', 41)));

        assertEquals("B", repository.findById(b.getId()).getName());
    }

    @Test
    public void delete_removesPresetAndItsStoredValues() {
        Preset a = repository.add("A", customSettings(), customSequence());
        Preset b = repository.add("B", new AppSettings(), new SequenceData());

        assertTrue(repository.delete(a.getId()));

        assertNull(repository.findById(a.getId()));
        assertEquals(1, repository.getAll().size());
        assertEquals("B", repository.getAll().get(0).getName());
        for (String key : store.values.keySet()) {
            assertFalse("Leftover key " + key, key.startsWith("preset." + a.getId() + "."));
        }
        assertNotNull(repository.findById(b.getId()));
    }

    @Test
    public void delete_unknownId_returnsFalse() {
        assertFalse(repository.delete(5));
    }

    @Test
    public void ids_areNotReusedAfterDelete() {
        Preset a = repository.add("A", new AppSettings(), new SequenceData());
        repository.delete(a.getId());

        Preset b = repository.add("B", new AppSettings(), new SequenceData());

        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    public void deletedName_canBeUsedAgain() {
        Preset a = repository.add("A", new AppSettings(), new SequenceData());
        repository.delete(a.getId());

        assertEquals(NameCheck.OK, repository.checkName("A", -1));
    }

    @Test
    public void missingStoredValues_fallBackToDefaults() {
        Preset preset = repository.add("Partial", customSettings(), customSequence());
        store.removeWithPrefix("preset." + preset.getId() + ".drop.");
        store.remove("preset." + preset.getId() + ".settings.numDrops");

        Preset loaded = repository.findById(preset.getId());

        assertNotNull(loaded);
        assertEquals(AppSettings.DEFAULT_NUM_DROPS, loaded.getSettings().getNumDrops());
        assertEquals(new SequenceData().getDrop(0), loaded.getSequence().getDrop(0));
        assertTrue(loaded.getSequence().getCamera().isEnabled());
    }

    @Test
    public void idWithoutName_isSkipped() {
        Preset a = repository.add("A", new AppSettings(), new SequenceData());
        repository.add("B", new AppSettings(), new SequenceData());
        store.remove("preset." + a.getId() + ".name");

        List<Preset> all = repository.getAll();

        assertEquals(1, all.size());
        assertEquals("B", all.get(0).getName());
    }

    @Test
    public void damagedIdList_isTolerated() {
        store.storeString("presets.ids", "x,,3,3");

        assertTrue(repository.getAll().isEmpty());
        Preset added = repository.add("A", new AppSettings(), new SequenceData());
        assertEquals(1, repository.getAll().size());
        assertEquals("A", repository.getAll().get(0).getName());
        assertEquals(added.getId(), repository.getAll().get(0).getId());
    }

    @Test
    public void presets_doNotTouchTheWorkingState() {
        StateStorage working = new StateStorage(store);
        AppSettings settings = new AppSettings();
        settings.setNumDrops(5);
        working.saveSettings(settings);

        repository.add("A", customSettings(), customSequence());

        AppSettings loaded = new AppSettings();
        working.loadSettings(loaded);
        assertEquals(5, loaded.getNumDrops());
    }

    @Test
    public void hasSameContentAs_ignoresIdNameAndSounds() {
        AppSettings s1 = customSettings();
        s1.setSoundsOn(false);
        Preset p1 = new Preset(1, "One", s1, customSequence());
        Preset p2 = new Preset(2, "Two", customSettings(), customSequence());

        assertTrue(p1.hasSameContentAs(p2));
    }

    @Test
    public void hasSameContentAs_detectsSettingAndDropChanges() {
        Preset base = new Preset(1, "A", customSettings(), customSequence());

        AppSettings otherSettings = customSettings();
        otherSettings.setWaitSeconds(43);
        assertFalse(base.hasSameContentAs(new Preset(2, "A", otherSettings, customSequence())));

        SequenceData otherSequence = customSequence();
        otherSequence.getDrop(8).setSize(556);
        assertFalse(base.hasSameContentAs(new Preset(2, "A", customSettings(), otherSequence)));
    }

    // ----------------------------------------------------
    // importAll
    // ----------------------------------------------------

    private static Preset incoming(String name, int startTime) {
        SequenceData data = new SequenceData();
        data.getDrop(0).setStartTime(startTime);
        return new Preset(Preset.NO_ID, name, new AppSettings(), data);
    }

    private static List<Preset> listOf(Preset... presets) {
        return new ArrayList<>(Arrays.asList(presets));
    }

    private static void assertCounts(PresetRepository.ImportResult result, int imported, int renamed, int skipped) {
        assertEquals("imported", imported, result.getImported());
        assertEquals("renamed", renamed, result.getRenamed());
        assertEquals("skipped", skipped, result.getSkipped());
    }

    @Test
    public void import_newNames_areSavedAsTheyAre() {
        PresetRepository.ImportResult result = repository.importAll(listOf(incoming("A", 1), incoming("B", 2)));

        assertCounts(result, 2, 0, 0);
        assertEquals(2, repository.getAll().size());
        assertEquals("A", repository.getAll().get(0).getName());
        assertEquals("B", repository.getAll().get(1).getName());
    }

    @Test
    public void import_sameNameAndContent_isSkipped() {
        repository.add("A", new AppSettings(), incoming("A", 10).getSequence());

        PresetRepository.ImportResult result = repository.importAll(listOf(incoming("a", 10)));

        assertCounts(result, 0, 0, 1);
        assertEquals(1, repository.getAll().size());
    }

    @Test
    public void import_contentComparison_ignoresSounds() {
        AppSettings quiet = new AppSettings();
        quiet.setSoundsOn(false);
        repository.add("A", quiet, incoming("A", 10).getSequence());

        assertCounts(repository.importAll(listOf(incoming("A", 10))), 0, 0, 1);
    }

    @Test
    public void import_sameNameDifferentContent_getsNumberAppended() {
        repository.add("A", new AppSettings(), incoming("A", 10).getSequence());

        PresetRepository.ImportResult result = repository.importAll(listOf(incoming("A", 20)));

        assertCounts(result, 0, 1, 0);
        assertEquals(2, repository.getAll().size());
        assertEquals("A1", repository.getAll().get(1).getName());
        assertEquals(20, repository.getAll().get(1).getSequence().getDrop(0).getStartTime());
    }

    @Test
    public void import_numberedNameTaken_usesFirstFreeNumber() {
        repository.add("A", new AppSettings(), incoming("A", 10).getSequence());
        repository.add("A1", new AppSettings(), incoming("A", 11).getSequence());

        PresetRepository.ImportResult result = repository.importAll(listOf(incoming("A", 20)));

        assertCounts(result, 0, 1, 0);
        assertNotNull(repository.findByName("A2"));
    }

    @Test
    public void import_sameFileTwice_addsNothingTheSecondTime() {
        repository.add("A", new AppSettings(), incoming("A", 10).getSequence());
        List<Preset> file = listOf(incoming("A", 20), incoming("B", 30));

        assertCounts(repository.importAll(file), 1, 1, 0);
        assertEquals(3, repository.getAll().size());

        assertCounts(repository.importAll(file), 0, 0, 2);
        assertEquals(3, repository.getAll().size());
    }

    @Test
    public void import_renamedCopyWithSameContent_isSkippedEvenIfNumberIsHigher() {
        repository.add("A", new AppSettings(), incoming("A", 10).getSequence());
        repository.add("A1", new AppSettings(), incoming("A", 20).getSequence());
        repository.add("A2", new AppSettings(), incoming("A", 30).getSequence());

        // Same content as A1: skipped, no A3 is created
        assertCounts(repository.importAll(listOf(incoming("A", 20))), 0, 0, 1);
        assertEquals(3, repository.getAll().size());
    }

    @Test
    public void import_duplicatesInsideTheFile_areHandledInOrder() {
        PresetRepository.ImportResult result = repository.importAll(
                listOf(incoming("A", 1), incoming("A", 1), incoming("A", 2)));

        assertCounts(result, 1, 1, 1);
        assertNotNull(repository.findByName("A1"));
        assertEquals(2, repository.getAll().size());
    }

    @Test
    public void import_longName_isShortenedForTheNumber() {
        String name = repeat('n', PresetRepository.MAX_NAME_LENGTH);
        repository.add(name, new AppSettings(), incoming(name, 10).getSequence());

        PresetRepository.ImportResult result = repository.importAll(listOf(incoming(name, 20)));

        assertCounts(result, 0, 1, 0);
        Preset added = repository.getAll().get(1);
        assertEquals(PresetRepository.MAX_NAME_LENGTH, added.getName().length());
        assertTrue(added.getName().endsWith("1"));
    }

    @Test
    public void import_emptyList_changesNothing() {
        assertCounts(repository.importAll(new ArrayList<>()), 0, 0, 0);
        assertTrue(repository.getAll().isEmpty());
    }

    @Test
    public void import_fromJsonFile_roundTripThroughExport() throws PresetJson.FormatException {
        repository.add("Crown", customSettings(), customSequence());
        String exported = PresetJson.write(repository.getAll());

        PresetRepository other = new PresetRepository(new MapStore());
        assertCounts(other.importAll(PresetJson.read(exported)), 1, 0, 0);
        assertTrue(other.findByName("Crown").hasSameContentAs(customSettings(), customSequence()));

        // Importing back into the source changes nothing
        assertCounts(repository.importAll(PresetJson.read(exported)), 0, 0, 1);
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
