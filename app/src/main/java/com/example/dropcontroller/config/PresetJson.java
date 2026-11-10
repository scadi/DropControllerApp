package com.example.dropcontroller.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.DeltaValue;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.Preset;
import com.example.dropcontroller.data.SequenceData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON file format of exported presets. Plain Java apart from {@code org.json}.
 * <p>
 * Layout: {@code {"format": "dropcontroller-presets", "version": 1, "presets": [ ... ]}}. A preset has a
 * {@code name}, {@code settings}, a {@code drops} list (up to 9), {@code camera} and a {@code flashes} list (up to 3).
 * Deltas are stored as {@code {"direction": "INC", "amount": 5}}. The Sounds switch is not part of a preset.
 * <p>
 * Reading is tolerant: missing or invalid fields fall back to the defaults and numbers are clamped by the model
 * setters, like in {@link StateStorage}. A file that is not valid JSON, has another format marker or version, or has
 * no {@code presets} list is rejected as a whole with a {@link FormatException}.
 */
public final class PresetJson {

    public static final String FORMAT = "dropcontroller-presets";
    public static final int VERSION = 1;

    /** Id of the presets returned by {@link #read(String)}: they are not stored yet. */
    public static final int NO_ID = Preset.NO_ID;

    /** The file cannot be imported. */
    public static class FormatException extends Exception {
        public FormatException(@NonNull String message) {
            super(message);
        }

        public FormatException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }

    private PresetJson() {
    }

    // ----------------------------------------------------
    // Writing
    // ----------------------------------------------------

    @NonNull
    public static String write(@NonNull List<Preset> presets) {
        try {
            JSONArray array = new JSONArray();
            for (Preset preset : presets) {
                array.put(presetToJson(preset));
            }
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("version", VERSION);
            root.put("presets", array);
            return root.toString(2);
        } catch (JSONException e) {
            // Only numbers, booleans and strings are written, so this cannot happen
            throw new IllegalStateException("Cannot write presets", e);
        }
    }

    @NonNull
    private static JSONObject presetToJson(@NonNull Preset preset) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", preset.getName());

        AppSettings settings = preset.getSettings();
        JSONObject settingsJson = new JSONObject();
        settingsJson.put("numDrops", settings.getNumDrops());
        settingsJson.put("loopMode", settings.isLoopModeOn());
        settingsJson.put("delayMethod", settings.getDelayMethod().name());
        settingsJson.put("waitSeconds", settings.getWaitSeconds());
        settingsJson.put("cameraPulseMs", settings.getCameraPulseMs());
        settingsJson.put("flashPulseMs", settings.getFlashPulseMs());
        json.put("settings", settingsJson);

        SequenceData sequence = preset.getSequence();
        JSONArray drops = new JSONArray();
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            DropData drop = sequence.getDrop(i);
            JSONObject dropJson = new JSONObject();
            dropJson.put("solenoid", drop.getSolenoid());
            dropJson.put("start", drop.getStartTime());
            dropJson.put("startDelta", deltaToJson(drop.getStartDelta()));
            dropJson.put("size", drop.getSize());
            dropJson.put("sizeDelta", deltaToJson(drop.getSizeDelta()));
            drops.put(dropJson);
        }
        json.put("drops", drops);

        CameraTrigger camera = sequence.getCamera();
        JSONObject cameraJson = new JSONObject();
        cameraJson.put("enabled", camera.isEnabled());
        cameraJson.put("bulb", camera.isBulb());
        cameraJson.put("time", camera.getTime());
        cameraJson.put("delta", deltaToJson(camera.getDelta()));
        json.put("camera", cameraJson);

        JSONArray flashes = new JSONArray();
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            FlashTrigger flash = sequence.getFlash(i);
            JSONObject flashJson = new JSONObject();
            flashJson.put("enabled", flash.isEnabled());
            flashJson.put("time", flash.getTime());
            flashJson.put("delta", deltaToJson(flash.getDelta()));
            flashes.put(flashJson);
        }
        json.put("flashes", flashes);
        return json;
    }

    @NonNull
    private static JSONObject deltaToJson(@NonNull DeltaValue delta) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("direction", delta.getDirection().name());
        json.put("amount", delta.getAmount());
        return json;
    }

    // ----------------------------------------------------
    // Reading
    // ----------------------------------------------------

    /**
     * Reads the presets of a file. Entries without a usable name are skipped; names are trimmed and shortened to
     * {@link PresetRepository#MAX_NAME_LENGTH} characters. The returned presets have the id {@link #NO_ID}.
     *
     * @throws FormatException if the file as a whole cannot be imported
     */
    @NonNull
    public static List<Preset> read(@NonNull String text) throws FormatException {
        JSONObject root;
        try {
            root = new JSONObject(stripBom(text));
        } catch (JSONException e) {
            throw new FormatException("Not a valid JSON file", e);
        }
        if (!FORMAT.equals(root.optString("format", null))) {
            throw new FormatException("Not a dropController presets file");
        }
        int version = root.optInt("version", -1);
        if (version < 1 || version > VERSION) {
            throw new FormatException("Unsupported file version: " + version);
        }
        JSONArray array = root.optJSONArray("presets");
        if (array == null) {
            throw new FormatException("The file has no presets list");
        }

        List<Preset> presets = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            Preset preset = json == null ? null : presetFromJson(json);
            if (preset != null) {
                presets.add(preset);
            }
        }
        return presets;
    }

    @Nullable
    private static Preset presetFromJson(@NonNull JSONObject json) {
        String name = json.optString("name", "").trim();
        if (name.length() > PresetRepository.MAX_NAME_LENGTH) {
            name = name.substring(0, PresetRepository.MAX_NAME_LENGTH).trim();
        }
        if (name.isEmpty()) {
            return null;
        }

        AppSettings defaults = new AppSettings();
        AppSettings settings = new AppSettings();
        JSONObject settingsJson = json.optJSONObject("settings");
        if (settingsJson != null) {
            settings.setNumDrops(settingsJson.optInt("numDrops", defaults.getNumDrops()));
            settings.setLoopModeOn(settingsJson.optBoolean("loopMode", defaults.isLoopModeOn()));
            settings.setDelayMethod(parseDelayMethod(settingsJson.optString("delayMethod", null)));
            settings.setWaitSeconds(settingsJson.optInt("waitSeconds", defaults.getWaitSeconds()));
            settings.setCameraPulseMs(settingsJson.optInt("cameraPulseMs", defaults.getCameraPulseMs()));
            settings.setFlashPulseMs(settingsJson.optInt("flashPulseMs", defaults.getFlashPulseMs()));
        }

        SequenceData sequence = new SequenceData();
        JSONArray drops = json.optJSONArray("drops");
        if (drops != null) {
            for (int i = 0; i < Math.min(drops.length(), Limits.MAX_DROPS); i++) {
                JSONObject dropJson = drops.optJSONObject(i);
                if (dropJson != null) {
                    readDrop(dropJson, sequence.getDrop(i));
                }
            }
        }
        JSONObject cameraJson = json.optJSONObject("camera");
        if (cameraJson != null) {
            CameraTrigger camera = sequence.getCamera();
            camera.setEnabled(cameraJson.optBoolean("enabled", false));
            camera.setBulb(cameraJson.optBoolean("bulb", false));
            camera.setTime(cameraJson.optInt("time", CameraTrigger.DEFAULT_TIME_MS));
            readDelta(cameraJson.optJSONObject("delta"), camera.getDelta());
        }
        JSONArray flashes = json.optJSONArray("flashes");
        if (flashes != null) {
            for (int i = 0; i < Math.min(flashes.length(), SequenceData.FLASH_COUNT); i++) {
                JSONObject flashJson = flashes.optJSONObject(i);
                if (flashJson != null) {
                    FlashTrigger flash = sequence.getFlash(i);
                    flash.setEnabled(flashJson.optBoolean("enabled", false));
                    flash.setTime(flashJson.optInt("time", FlashTrigger.DEFAULT_TIME_MS));
                    readDelta(flashJson.optJSONObject("delta"), flash.getDelta());
                }
            }
        }
        return new Preset(NO_ID, name, settings, sequence);
    }

    private static void readDrop(@NonNull JSONObject json, @NonNull DropData drop) {
        drop.setSolenoid(json.optInt("solenoid", DropData.DEFAULT_SOLENOID));
        drop.setStartTime(json.optInt("start", DropData.DEFAULT_START_MS));
        readDelta(json.optJSONObject("startDelta"), drop.getStartDelta());
        drop.setSize(json.optInt("size", DropData.DEFAULT_SIZE_MS));
        readDelta(json.optJSONObject("sizeDelta"), drop.getSizeDelta());
    }

    private static void readDelta(@Nullable JSONObject json, @NonNull DeltaValue delta) {
        if (json == null) {
            return;
        }
        delta.setDirection(parseDirection(json.optString("direction", null)));
        delta.setAmount(json.optInt("amount", 0));
    }

    @NonNull
    private static DeltaDirection parseDirection(@Nullable String name) {
        if (name != null) {
            try {
                return DeltaDirection.valueOf(name);
            } catch (IllegalArgumentException e) {
                // Unknown value; use the default
            }
        }
        return DeltaDirection.INC;
    }

    @NonNull
    private static LoopDelayMethod parseDelayMethod(@Nullable String name) {
        if (name != null) {
            try {
                return LoopDelayMethod.valueOf(name);
            } catch (IllegalArgumentException e) {
                // Unknown value; use the default
            }
        }
        return new AppSettings().getDelayMethod();
    }

    @NonNull
    private static String stripBom(@NonNull String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
