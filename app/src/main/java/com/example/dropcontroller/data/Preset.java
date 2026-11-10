package com.example.dropcontroller.data;

import androidx.annotation.NonNull;

/**
 * A named, saved setup: the settings that describe it and all drop data. The Sounds switch is not part of a
 * preset (see {@link AppSettings#copySetupFrom(AppSettings)}).
 */
public class Preset {

    /** Id of a preset that is not stored in the repository (for example one just read from a file). */
    public static final int NO_ID = -1;

    private final int id;
    private String name;
    private final AppSettings settings = new AppSettings();
    private final SequenceData sequence = new SequenceData();

    public Preset(int id, @NonNull String name, @NonNull AppSettings settings, @NonNull SequenceData sequence) {
        this.id = id;
        this.name = name;
        this.settings.copySetupFrom(settings);
        this.sequence.copyFrom(sequence);
    }

    public int getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public AppSettings getSettings() {
        return settings;
    }

    @NonNull
    public SequenceData getSequence() {
        return sequence;
    }

    /**
     * Compares the content only: ignores the id, the name and the Sounds switch.
     */
    public boolean hasSameContentAs(@NonNull Preset other) {
        return settings.hasSameSetupAs(other.settings) && sequence.equals(other.sequence);
    }

    /**
     * Compares the content with the current settings and drop data.
     */
    public boolean hasSameContentAs(@NonNull AppSettings otherSettings, @NonNull SequenceData otherSequence) {
        return settings.hasSameSetupAs(otherSettings) && sequence.equals(otherSequence);
    }
}
