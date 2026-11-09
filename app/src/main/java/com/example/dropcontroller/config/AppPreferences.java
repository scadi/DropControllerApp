package com.example.dropcontroller.config;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Helper class for handling TinyDB operations (SharedPreferences)
public class AppPreferences implements KeyValueStore {

    private static final String PREF_NAME = "DropControllerSettings"; // TinyDB name
    private final SharedPreferences sharedPreferences;

    public AppPreferences(@NonNull Context context) {
        // Get the preference file with the defined name
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Analogous to TinyDB.StoreValue(tag, value) ---
    @Override
    public void storeString(@NonNull String tag, @Nullable String value) {
        sharedPreferences.edit().putString(tag, value).apply();
    }

    // --- Analogous to TinyDB.GetValue(tag, defaultValue) ---
    @Override
    @Nullable
    public String getString(@NonNull String tag, @Nullable String defaultValue) {
        return sharedPreferences.getString(tag, defaultValue);
    }

    @Override
    public void storeInt(@NonNull String tag, int value) {
        sharedPreferences.edit().putInt(tag, value).apply();
    }

    @Override
    public int getInt(@NonNull String tag, int defaultValue) {
        return sharedPreferences.getInt(tag, defaultValue);
    }

    @Override
    public void storeBoolean(@NonNull String tag, boolean value) {
        sharedPreferences.edit().putBoolean(tag, value).apply();
    }

    @Override
    public boolean getBoolean(@NonNull String tag, boolean defaultValue) {
        return sharedPreferences.getBoolean(tag, defaultValue);
    }
}
