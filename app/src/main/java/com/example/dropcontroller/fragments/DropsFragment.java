package com.example.dropcontroller.fragments;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.LoopActivity;
import com.example.dropcontroller.R;
import com.example.dropcontroller.config.PresetRepository;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.Preset;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.services.BluetoothHelper;
import com.example.dropcontroller.services.SequenceRunner;
import com.example.dropcontroller.views.ValueDeltaRow;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops tab: data of the active drops, camera and flash triggers, and the START button.
 * <p>
 * Single Sequence Mode (Loop Mode OFF): START sends the drop data immediately and deltas are ignored.
 * Loop Mode: START opens the Loop Page ({@link LoopActivity}).
 * The button is grey when not connected, green when connected and shows ACTIVE while the device runs the sequence.
 */
public class DropsFragment extends Fragment {

    // Color scale of the drop cards: start position and the alternating steps (share of the lightest-darkest range)
    private static final float CARD_SHADE_START = 0.20f;
    private static final float CARD_SHADE_UP = 0.40f;
    private static final float CARD_SHADE_DOWN = -0.34f;

    private DropControllerApp app;
    private BluetoothHelper bluetoothHelper;
    private SequenceRunner sequenceRunner;

    // Cards are created on demand: only as many as the number of drops needs (inflating all of them is slow)
    private final List<View> dropViews = new ArrayList<>();
    private final List<DropRow> dropRows = new ArrayList<>();
    private LinearLayout dropList;
    private int[] cardColors;
    private ValueDeltaRow cameraRow;
    private final ValueDeltaRow[] flashRows = new ValueDeltaRow[SequenceData.FLASH_COUNT];
    private MaterialSwitch bulbSwitch;
    private View triggersCard;
    private Button startButton;
    private Button saveButton;
    private Button loadButton;
    private AlertDialog dialog;

    // True while the views are filled from the model, so the change listeners do not write back
    private boolean binding;

    private static final class DropRow {
        final Spinner solenoid;
        final ValueDeltaRow start;
        final ValueDeltaRow size;

        DropRow(Spinner solenoid, ValueDeltaRow start, ValueDeltaRow size) {
            this.solenoid = solenoid;
            this.start = start;
            this.size = size;
        }
    }

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            updateStartButton();
        }

        @Override
        public void onError(int messageResId) {
            if (isAdded()) {
                Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show();
            }
        }
    };

    private final SequenceRunner.Listener sequenceListener = new SequenceRunner.Listener() {
        @Override
        public void onActiveChanged(boolean active) {
            updateStartButton();
        }

        @Override
        public void onFinished(boolean timedOut) {
            if (timedOut && isAdded()) {
                Toast.makeText(requireContext(), R.string.sequence_timeout, Toast.LENGTH_LONG).show();
            }
        }
    };

    public DropsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drops, container, false);

        app = (DropControllerApp) requireActivity().getApplication();
        bluetoothHelper = app.getBluetoothHelper();
        sequenceRunner = app.getSequenceRunner();
        SequenceData sequence = app.getSequence();

        dropList = view.findViewById(R.id.ll_drops);
        int lightest = ContextCompat.getColor(requireContext(), R.color.drop_card_lightest);
        int darkest = ContextCompat.getColor(requireContext(), R.color.drop_card_darkest);
        cardColors = new int[Limits.MAX_DROPS];
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            cardColors[i] = ColorUtils.blendARGB(lightest, darkest, getCardShade(i));
        }
        ensureDropCards(inflater, app.getSettings().getNumDrops());

        createTriggerRows(view, sequence);

        startButton = view.findViewById(R.id.btn_start);
        startButton.setOnClickListener(v -> onStartClick());
        saveButton = view.findViewById(R.id.btn_preset_save);
        saveButton.setOnClickListener(v -> onSaveClick());
        loadButton = view.findViewById(R.id.btn_preset_load);
        loadButton.setOnClickListener(v -> onLoadClick());

        bindViews();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        bluetoothHelper.addListener(bluetoothListener);
        sequenceRunner.addListener(sequenceListener);
        // Settings may have changed on the Settings tab
        bindViews();
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetoothHelper.removeListener(bluetoothListener);
        sequenceRunner.removeListener(sequenceListener);
        // A field that still has focus has not been committed yet
        commitAll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Avoid a leaked dialog window when the view is re-created (rotation)
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    // ----------------------------------------------------
    // Building the rows
    // ----------------------------------------------------

    /**
     * Creates the missing drop cards, so that the first {@code count} drops have one.
     */
    private void ensureDropCards(@NonNull LayoutInflater inflater, int count) {
        SequenceData sequence = app.getSequence();
        for (int i = dropViews.size(); i < count; i++) {
            View item = inflateDropCard(inflater, dropList, cardColors[i]);
            dropList.addView(item);
            dropViews.add(item);
            dropRows.add(createDropRow(item, i, sequence.getDrop(i)));
        }
    }

    /**
     * Position of a drop card on the color scale: 0 is the lightest color, 1 the darkest. Drop 1 starts at 20%, then
     * the position goes up by 40% and down by 34% in turn (20, 60, 26, 66, 32, 72, ...), so the cards alternate
     * between light and dark and slowly get darker overall.
     */
    private static float getCardShade(int index) {
        int steps = index / 2;
        float shade = CARD_SHADE_START + steps * (CARD_SHADE_UP + CARD_SHADE_DOWN);
        return index % 2 == 0 ? shade : shade + CARD_SHADE_UP;
    }

    /**
     * Inflates a drop card with the given background color. The card content is inflated with a theme overlay
     * (light or dark text, whichever has the better contrast), so the fields stay readable on every color.
     */
    @NonNull
    private View inflateDropCard(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent,
                                 @ColorInt int cardColor) {
        boolean lightText = ColorUtils.calculateContrast(Color.WHITE, cardColor)
                > ColorUtils.calculateContrast(ContextCompat.getColor(requireContext(), R.color.drop_card_dark_text),
                cardColor);
        Context themed = new ContextThemeWrapper(requireContext(), lightText
                ? R.style.ThemeOverlay_DropController_CardLightText
                : R.style.ThemeOverlay_DropController_CardDarkText);
        View item = inflater.cloneInContext(themed).inflate(R.layout.item_drop, parent, false);
        MaterialCardView card = (MaterialCardView) item;
        card.setCardBackgroundColor(cardColor);
        card.setStrokeColor(ColorUtils.blendARGB(cardColor, Color.BLACK, 0.15f));
        return item;
    }

    @NonNull
    private DropRow createDropRow(@NonNull View item, int index, @NonNull DropData drop) {
        // The drop icon is part of the layout; the full name is for screen readers
        TextView title = item.findViewById(R.id.tv_drop_title);
        title.setText(String.valueOf(index + 1));
        title.setContentDescription(getString(R.string.drop_title, index + 1));

        Spinner solenoid = item.findViewById(R.id.sp_solenoid);
        solenoid.setSaveEnabled(false);
        List<String> values = new ArrayList<>();
        for (int sol = Limits.MIN_SOLENOID; sol <= Limits.MAX_SOLENOID; sol++) {
            values.add(String.valueOf(sol));
        }
        // The selected value is drawn on the card, so it gets the card's text color; the popup keeps its own
        int valueColor = MaterialColors.getColor(item, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, values) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        TextView text = (TextView) super.getView(position, convertView, parent);
                        text.setTextColor(valueColor);
                        return text;
                    }
                };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        solenoid.setAdapter(adapter);
        solenoid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                if (!binding) {
                    drop.setSolenoid(position + Limits.MIN_SOLENOID);
                    app.saveSequence();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Nothing to do
            }
        });

        ValueDeltaRow start = new ValueDeltaRow(item.findViewById(R.id.row_start), drop.getStartDelta(),
                drop::getStartTime, drop::setStartTime, app::saveSequence);
        start.setLabel(R.string.drop_start);
        ValueDeltaRow size = new ValueDeltaRow(item.findViewById(R.id.row_size), drop.getSizeDelta(),
                drop::getSize, drop::setSize, app::saveSequence);
        size.setLabel(R.string.drop_size);
        return new DropRow(solenoid, start, size);
    }

    private void createTriggerRows(@NonNull View view, @NonNull SequenceData sequence) {
        triggersCard = view.findViewById(R.id.card_triggers);

        CameraTrigger camera = sequence.getCamera();
        cameraRow = new ValueDeltaRow(view.findViewById(R.id.row_camera), camera.getDelta(),
                camera::getTime, camera::setTime, app::saveSequence);
        cameraRow.setIconLabel(R.drawable.ic_camera_black_24dp, "", getString(R.string.trigger_camera));

        // Bulb sits in the label line of the camera row, next to the camera icon
        bulbSwitch = cameraRow.getExtraSwitch();
        bulbSwitch.setText(R.string.trigger_bulb);
        bulbSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                camera.setBulb(checked);
                app.saveSequence();
                updateTriggerStates();
            }
        });

        int[] flashRowIds = {R.id.row_flash1, R.id.row_flash2, R.id.row_flash3};
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            FlashTrigger flash = sequence.getFlash(i);
            flashRows[i] = new ValueDeltaRow(view.findViewById(flashRowIds[i]), flash.getDelta(),
                    flash::getTime, flash::setTime, app::saveSequence);
            flashRows[i].setIconLabel(R.drawable.ic_flash_black_24dp, String.valueOf(i + 1),
                    getString(R.string.trigger_flash, i + 1));
        }
    }

    // ----------------------------------------------------
    // Model <-> views
    // ----------------------------------------------------

    private void bindViews() {
        AppSettings settings = app.getSettings();
        SequenceData sequence = app.getSequence();

        // The number of drops may have grown on the Settings tab or by loading a preset
        ensureDropCards(getLayoutInflater(), settings.getNumDrops());

        binding = true;
        for (int i = 0; i < dropViews.size(); i++) {
            // Only the selected number of drops is active
            dropViews.get(i).setVisibility(i < settings.getNumDrops() ? View.VISIBLE : View.GONE);
            DropRow row = dropRows.get(i);
            row.solenoid.setSelection(sequence.getDrop(i).getSolenoid() - Limits.MIN_SOLENOID);
            row.start.bind();
            row.size.bind();
        }
        cameraRow.bind();
        bulbSwitch.setChecked(sequence.getCamera().isBulb());
        for (ValueDeltaRow row : flashRows) {
            row.bind();
        }
        binding = false;

        updateTriggerStates();
        updateStartButton();
    }

    /**
     * Hides the controls of a trigger that is OFF in the Settings tab (and the whole card when all are OFF).
     * In Bulb mode the camera time and delta are not used, so that row is disabled.
     */
    private void updateTriggerStates() {
        SequenceData sequence = app.getSequence();
        CameraTrigger camera = sequence.getCamera();
        boolean anyVisible = camera.isEnabled();

        cameraRow.setVisible(camera.isEnabled());
        cameraRow.setEnabled(!camera.isBulb());
        bulbSwitch.setVisibility(camera.isEnabled() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            boolean enabled = sequence.getFlash(i).isEnabled();
            flashRows[i].setVisible(enabled);
            anyVisible |= enabled;
        }
        triggersCard.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Stores the text of every field in the model and saves it once.
     */
    private void commitAll() {
        if (app == null) {
            return;
        }
        for (int i = 0; i < Math.min(app.getSettings().getNumDrops(), dropRows.size()); i++) {
            dropRows.get(i).start.apply();
            dropRows.get(i).size.apply();
        }
        cameraRow.apply();
        for (ValueDeltaRow row : flashRows) {
            row.apply();
        }
        app.saveSequence();
    }

    // ----------------------------------------------------
    // START button
    // ----------------------------------------------------

    private void updateStartButton() {
        if (startButton == null) {
            return;
        }
        boolean connected = bluetoothHelper.isConnected();
        boolean active = sequenceRunner.isActive();

        // Each state has its own text color, so the label stays readable on grey, green and yellow
        int colorRes;
        int textColorRes;
        if (active) {
            colorRes = R.color.start_active;
            textColorRes = R.color.start_active_text;
            startButton.setText(R.string.btn_start_active);
        } else {
            colorRes = connected ? R.color.start_ready : R.color.start_inactive;
            textColorRes = connected ? R.color.start_ready_text : R.color.start_inactive_text;
            startButton.setText(R.string.btn_start);
        }
        startButton.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes)));
        startButton.setTextColor(ContextCompat.getColor(requireContext(), textColorRes));
        startButton.setEnabled(connected && !active);
        // Presets cannot change while a sequence is running
        saveButton.setEnabled(!active);
        loadButton.setEnabled(!active);
    }

    private void onStartClick() {
        commitAll();
        if (app.getSettings().isLoopModeOn()) {
            // Loop Mode: START opens the Loop Page, which sends the sequences
            startActivity(new Intent(requireContext(), LoopActivity.class));
            return;
        }
        // Single Sequence Mode: send the data as it is, deltas are ignored
        sequenceRunner.start(1);
    }

    // ----------------------------------------------------
    // Presets
    // ----------------------------------------------------

    private void onSaveClick() {
        if (sequenceRunner.isActive()) {
            return;
        }
        // Store the open fields first, so the preset contains what is on the screen
        commitAll();

        View content = getLayoutInflater().inflate(R.layout.dialog_preset_name, null);
        TextInputLayout inputLayout = content.findViewById(R.id.til_preset_name);
        TextInputEditText input = content.findViewById(R.id.et_preset_name);

        AlertDialog saveDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preset_save_title)
                .setView(content)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        saveDialog.setOnShowListener(d -> {
            // Set the click listener here, so an invalid name keeps the dialog open
            saveDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> confirmSave(saveDialog, inputLayout, input));
            input.requestFocus();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmSave(saveDialog, inputLayout, input);
                return true;
            }
            return false;
        });
        saveDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        showDialog(saveDialog);
    }

    private void confirmSave(@NonNull AlertDialog saveDialog, @NonNull TextInputLayout inputLayout,
                             @NonNull TextInputEditText input) {
        PresetRepository repository = app.getPresetRepository();
        String name = input.getText() == null ? "" : input.getText().toString().trim();

        Preset existing = repository.findByName(name);
        PresetRepository.NameCheck check = repository.checkName(name, existing == null ? -1 : existing.getId());
        if (check == PresetRepository.NameCheck.EMPTY) {
            inputLayout.setError(getString(R.string.preset_name_empty));
            return;
        }
        if (check == PresetRepository.NameCheck.TOO_LONG) {
            inputLayout.setError(getString(R.string.preset_name_too_long, PresetRepository.MAX_NAME_LENGTH));
            return;
        }
        inputLayout.setError(null);

        if (existing != null) {
            confirmOverwrite(saveDialog, existing, name);
            return;
        }
        repository.add(name, app.getSettings(), app.getSequence());
        saveDialog.dismiss();
        Toast.makeText(requireContext(), getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show();
    }

    private void confirmOverwrite(@NonNull AlertDialog saveDialog, @NonNull Preset existing, @NonNull String name) {
        AlertDialog overwriteDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preset_overwrite_title)
                .setMessage(getString(R.string.preset_overwrite_message, existing.getName()))
                .setPositiveButton(R.string.preset_overwrite, (d, which) -> {
                    app.getPresetRepository().overwrite(existing.getId(), name, app.getSettings(), app.getSequence());
                    saveDialog.dismiss();
                    Toast.makeText(requireContext(), getString(R.string.preset_saved, name), Toast.LENGTH_SHORT)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        // The save dialog stays behind it, so Cancel returns to the name field
        overwriteDialog.show();
    }

    private void onLoadClick() {
        if (sequenceRunner.isActive()) {
            return;
        }
        List<Preset> presets = app.getPresetRepository().getAll();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preset_load_title)
                .setNegativeButton(android.R.string.cancel, null);
        if (presets.isEmpty()) {
            builder.setMessage(R.string.preset_load_empty);
        } else {
            String[] names = new String[presets.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = presets.get(i).getName();
            }
            builder.setItems(names, (d, which) -> loadPreset(presets.get(which)));
        }
        showDialog(builder.create());
    }

    private void loadPreset(@NonNull Preset preset) {
        if (!app.applyPreset(preset)) {
            Toast.makeText(requireContext(), R.string.preset_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        bindViews();
        Toast.makeText(requireContext(), getString(R.string.preset_loaded, preset.getName()), Toast.LENGTH_SHORT)
                .show();
    }

    private void showDialog(@NonNull AlertDialog newDialog) {
        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = newDialog;
        newDialog.show();
    }
}
