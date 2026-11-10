package com.example.dropcontroller.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory {@link KeyValueStore} for JVM tests.
 */
class MapStore implements KeyValueStore {

    final Map<String, Object> values = new HashMap<>();

    @Override
    public void storeString(@NonNull String tag, @Nullable String value) {
        values.put(tag, value);
    }

    @Nullable
    @Override
    public String getString(@NonNull String tag, @Nullable String defaultValue) {
        return values.containsKey(tag) ? (String) values.get(tag) : defaultValue;
    }

    @Override
    public void storeInt(@NonNull String tag, int value) {
        values.put(tag, value);
    }

    @Override
    public int getInt(@NonNull String tag, int defaultValue) {
        return values.containsKey(tag) ? (Integer) values.get(tag) : defaultValue;
    }

    @Override
    public void storeBoolean(@NonNull String tag, boolean value) {
        values.put(tag, value);
    }

    @Override
    public boolean getBoolean(@NonNull String tag, boolean defaultValue) {
        return values.containsKey(tag) ? (Boolean) values.get(tag) : defaultValue;
    }

    @Override
    public void remove(@NonNull String tag) {
        values.remove(tag);
    }

    @Override
    public void removeWithPrefix(@NonNull String prefix) {
        values.keySet().removeIf(tag -> tag.startsWith(prefix));
    }
}
