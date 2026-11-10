package com.example.dropcontroller.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.Preset;
import com.example.dropcontroller.data.SequenceData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Saved presets (named setups) on top of a {@link KeyValueStore}. Plain Java, no Android dependencies.
 * <p>
 * Layout in the store: {@code presets.ids} (comma separated ids in creation order), {@code presets.nextId},
 * {@code preset.<id>.name} and the settings and drop data of the preset under the prefix {@code preset.<id>.}
 * (written by {@link StateStorage}). Names are unique, compared ignoring case.
 */
public class PresetRepository {

    public static final int MAX_NAME_LENGTH = 40;

    /** Result of checking a preset name. */
    public enum NameCheck {
        OK,
        EMPTY,
        TOO_LONG,
        DUPLICATE
    }

    /** Counts reported by {@link #importAll(List)}. */
    public static final class ImportResult {
        private final int imported;
        private final int renamed;
        private final int skipped;

        public ImportResult(int imported, int renamed, int skipped) {
            this.imported = imported;
            this.renamed = renamed;
            this.skipped = skipped;
        }

        /** Presets saved under their own name. */
        public int getImported() {
            return imported;
        }

        /** Presets saved under another name because their name was taken by different content. */
        public int getRenamed() {
            return renamed;
        }

        /** Presets that were already stored with identical content. */
        public int getSkipped() {
            return skipped;
        }
    }

    private static final String KEY_IDS = "presets.ids";
    private static final String KEY_NEXT_ID = "presets.nextId";

    private final KeyValueStore store;

    public PresetRepository(@NonNull KeyValueStore store) {
        this.store = store;
    }

    /**
     * Returns the presets in the order they were created. Ids without a stored name are skipped.
     */
    @NonNull
    public List<Preset> getAll() {
        List<Preset> presets = new ArrayList<>();
        for (int id : readIds()) {
            Preset preset = load(id);
            if (preset != null) {
                presets.add(preset);
            }
        }
        return presets;
    }

    @Nullable
    public Preset findById(int id) {
        if (!readIds().contains(id)) {
            return null;
        }
        return load(id);
    }

    /**
     * Finds a preset by name, ignoring case and surrounding spaces.
     */
    @Nullable
    public Preset findByName(@NonNull String name) {
        String wanted = name.trim();
        for (Preset preset : getAll()) {
            if (preset.getName().equalsIgnoreCase(wanted)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Checks a name (trimmed) for a preset.
     *
     * @param ignoreId id of the preset being renamed or overwritten, which may keep its own name; -1 for none
     */
    @NonNull
    public NameCheck checkName(@NonNull String name, int ignoreId) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return NameCheck.EMPTY;
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return NameCheck.TOO_LONG;
        }
        Preset existing = findByName(trimmed);
        if (existing != null && existing.getId() != ignoreId) {
            return NameCheck.DUPLICATE;
        }
        return NameCheck.OK;
    }

    /**
     * Adds a new preset with a copy of the given setup.
     *
     * @throws IllegalArgumentException if the name is not accepted by {@link #checkName(String, int)}
     */
    @NonNull
    public Preset add(@NonNull String name, @NonNull AppSettings settings, @NonNull SequenceData sequence) {
        requireValidName(name, -1);
        int id = store.getInt(KEY_NEXT_ID, 1);
        List<Integer> ids = readIds();
        while (ids.contains(id)) {
            id++;
        }
        store.storeInt(KEY_NEXT_ID, id + 1);
        ids.add(id);
        writeIds(ids);
        Preset preset = new Preset(id, name.trim(), settings, sequence);
        write(preset);
        return preset;
    }

    /**
     * Replaces the content (and the name) of an existing preset.
     *
     * @return the updated preset, or null if there is no preset with this id
     * @throws IllegalArgumentException if the name is not accepted by {@link #checkName(String, int)}
     */
    @Nullable
    public Preset overwrite(int id, @NonNull String name, @NonNull AppSettings settings,
                            @NonNull SequenceData sequence) {
        if (!readIds().contains(id)) {
            return null;
        }
        requireValidName(name, id);
        Preset preset = new Preset(id, name.trim(), settings, sequence);
        write(preset);
        return preset;
    }

    /**
     * Renames a preset.
     *
     * @return {@link NameCheck#OK} when renamed, otherwise the reason it was rejected (nothing is changed)
     */
    @NonNull
    public NameCheck rename(int id, @NonNull String newName) {
        NameCheck check = checkName(newName, id);
        if (check != NameCheck.OK || !readIds().contains(id)) {
            return check;
        }
        store.storeString(nameKey(id), newName.trim());
        return NameCheck.OK;
    }

    /**
     * @return true if a preset with this id existed and was removed
     */
    public boolean delete(int id) {
        List<Integer> ids = readIds();
        if (!ids.remove((Integer) id)) {
            return false;
        }
        writeIds(ids);
        store.removeWithPrefix(prefix(id));
        return true;
    }

    /**
     * Imports presets (for example read by {@link PresetJson#read(String)}). Per preset:
     * <ul>
     * <li>same name and identical content as a stored preset: skipped;</li>
     * <li>same name, different content: saved under the name with the first free number appended
     * ({@code Name1}, {@code Name2}, ...); if a preset with that name and identical content exists, it is skipped,
     * so importing the same file twice adds nothing. The name is shortened to stay within
     * {@link #MAX_NAME_LENGTH} characters;</li>
     * <li>otherwise: saved under its own name.</li>
     * </ul>
     * Names are compared ignoring case. Presets are checked in list order, so duplicates inside the list are
     * handled like duplicates of stored presets.
     */
    @NonNull
    public ImportResult importAll(@NonNull List<Preset> presets) {
        int imported = 0;
        int renamed = 0;
        int skipped = 0;
        for (Preset preset : presets) {
            String name = preset.getName().trim();
            if (checkName(name, -1) == NameCheck.EMPTY || checkName(name, -1) == NameCheck.TOO_LONG) {
                skipped++;
                continue;
            }
            Preset existing = findByName(name);
            if (existing == null) {
                add(name, preset.getSettings(), preset.getSequence());
                imported++;
            } else if (existing.hasSameContentAs(preset)) {
                skipped++;
            } else {
                String free = findFreeNameOrSkip(name, preset);
                if (free == null) {
                    skipped++;
                } else {
                    add(free, preset.getSettings(), preset.getSequence());
                    renamed++;
                }
            }
        }
        return new ImportResult(imported, renamed, skipped);
    }

    /**
     * Returns the first name {@code base + number} that is not used, or null if a preset with such a name and
     * the same content as {@code preset} exists before a free name is found.
     */
    @Nullable
    private String findFreeNameOrSkip(@NonNull String base, @NonNull Preset preset) {
        for (int number = 1; ; number++) {
            String suffix = String.valueOf(number);
            String head = base;
            if (head.length() + suffix.length() > MAX_NAME_LENGTH) {
                head = head.substring(0, MAX_NAME_LENGTH - suffix.length()).trim();
            }
            String candidate = head + suffix;
            Preset existing = findByName(candidate);
            if (existing == null) {
                return candidate;
            }
            if (existing.hasSameContentAs(preset)) {
                return null;
            }
        }
    }

    private void requireValidName(@NonNull String name, int ignoreId) {
        NameCheck check = checkName(name, ignoreId);
        if (check != NameCheck.OK) {
            throw new IllegalArgumentException("Invalid preset name: " + check);
        }
    }

    @Nullable
    private Preset load(int id) {
        String name = store.getString(nameKey(id), null);
        if (name == null) {
            return null;
        }
        StateStorage storage = new StateStorage(new PrefixedKeyValueStore(store, prefix(id)));
        AppSettings settings = new AppSettings();
        SequenceData sequence = new SequenceData();
        storage.loadSettings(settings);
        storage.loadSequence(sequence);
        return new Preset(id, name, settings, sequence);
    }

    private void write(@NonNull Preset preset) {
        store.storeString(nameKey(preset.getId()), preset.getName());
        StateStorage storage = new StateStorage(new PrefixedKeyValueStore(store, prefix(preset.getId())));
        storage.saveSettings(preset.getSettings());
        storage.saveSequence(preset.getSequence());
    }

    @NonNull
    private List<Integer> readIds() {
        List<Integer> ids = new ArrayList<>();
        String stored = store.getString(KEY_IDS, "");
        if (stored == null || stored.isEmpty()) {
            return ids;
        }
        for (String part : stored.split(",")) {
            try {
                Integer id = Integer.valueOf(part.trim());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException e) {
                // Ignore a damaged entry in the id list
            }
        }
        return ids;
    }

    private void writeIds(@NonNull List<Integer> ids) {
        StringBuilder builder = new StringBuilder();
        for (int id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%d", id));
        }
        store.storeString(KEY_IDS, builder.toString());
    }

    @NonNull
    private static String prefix(int id) {
        return "preset." + id + ".";
    }

    @NonNull
    private static String nameKey(int id) {
        return prefix(id) + "name";
    }
}
