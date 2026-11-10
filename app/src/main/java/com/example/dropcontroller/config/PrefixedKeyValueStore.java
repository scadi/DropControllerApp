package com.example.dropcontroller.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stores every value of another {@link KeyValueStore} under a fixed tag prefix, so several independent copies
 * of the same data (for example {@link StateStorage}) can share one store.
 */
public class PrefixedKeyValueStore implements KeyValueStore {

    private final KeyValueStore base;
    private final String prefix;

    public PrefixedKeyValueStore(@NonNull KeyValueStore base, @NonNull String prefix) {
        this.base = base;
        this.prefix = prefix;
    }

    @Override
    public void storeString(@NonNull String tag, @Nullable String value) {
        base.storeString(prefix + tag, value);
    }

    @Nullable
    @Override
    public String getString(@NonNull String tag, @Nullable String defaultValue) {
        return base.getString(prefix + tag, defaultValue);
    }

    @Override
    public void storeInt(@NonNull String tag, int value) {
        base.storeInt(prefix + tag, value);
    }

    @Override
    public int getInt(@NonNull String tag, int defaultValue) {
        return base.getInt(prefix + tag, defaultValue);
    }

    @Override
    public void storeBoolean(@NonNull String tag, boolean value) {
        base.storeBoolean(prefix + tag, value);
    }

    @Override
    public boolean getBoolean(@NonNull String tag, boolean defaultValue) {
        return base.getBoolean(prefix + tag, defaultValue);
    }

    @Override
    public void remove(@NonNull String tag) {
        base.remove(prefix + tag);
    }

    @Override
    public void removeWithPrefix(@NonNull String tagPrefix) {
        base.removeWithPrefix(prefix + tagPrefix);
    }
}
