package com.example.dropcontroller.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Minimal key/value storage (Analogous to TinyDB). Implemented by {@link AppPreferences};
 * tests use an in-memory implementation.
 */
public interface KeyValueStore {

    void storeString(@NonNull String tag, @Nullable String value);

    @Nullable
    String getString(@NonNull String tag, @Nullable String defaultValue);

    void storeInt(@NonNull String tag, int value);

    int getInt(@NonNull String tag, int defaultValue);

    void storeBoolean(@NonNull String tag, boolean value);

    boolean getBoolean(@NonNull String tag, boolean defaultValue);

    /**
     * Removes the value stored under the tag. Does nothing when there is none.
     */
    void remove(@NonNull String tag);

    /**
     * Removes every value whose tag starts with the prefix.
     */
    void removeWithPrefix(@NonNull String prefix);
}
