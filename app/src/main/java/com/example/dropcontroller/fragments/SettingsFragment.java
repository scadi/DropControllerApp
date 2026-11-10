package com.example.dropcontroller.fragments;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.R;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.data.ThemeMode;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Settings tab: connection, number of drops, Loop Mode, trigger pulse times, trigger enable switches,
 * sounds and RESET. Changes are applied to the shared {@link AppSettings} / {@link SequenceData} and saved at once.
 */
public class SettingsFragment extends Fragment {

    // Quick values for the trigger pulse times (ms)
    private static final int[] QUICK_PULSE_MS = {20, 50, 100, 200};
    // The wait time slider covers the common range; longer times are typed into the field
    private static final int SLIDER_MAX_WAIT_SECONDS = 120;
    // Loop Mode options look dimmed (but stay editable) while Loop Mode is OFF
    private static final float DIMMED_ALPHA = 0.4f;

    private DropControllerApp app;
    private BluetoothHelper bluetoothHelper;

    private Button connectButton;
    private LinearLayout numDropsRow;
    private TextView numDropsValue;
    private MaterialSwitch loopModeSwitch;
    private View loopOptions;
    private MaterialButtonToggleGroup delayMethodGroup;
    private EditText waitSecondsField;
    private Slider waitSecondsSlider;
    private EditText cameraPulseField;
    private EditText flashPulseField;
    private ChipGroup cameraPulseChips;
    private ChipGroup flashPulseChips;
    private Chip cameraChip;
    private Chip bulbChip;
    private final Chip[] flashChips = new Chip[SequenceData.FLASH_COUNT];
    private MaterialSwitch soundsSwitch;
    private MaterialButtonToggleGroup themeGroup;

    private AlertDialog dialog;

    // True while the views are filled from the model, so the change listeners do not write back
    private boolean binding;

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            updateConnectButton(state);
        }

        @Override
        public void onError(int messageResId) {
            if (isAdded()) {
                Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show();
            }
        }
    };

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        app = (DropControllerApp) requireActivity().getApplication();
        bluetoothHelper = app.getBluetoothHelper();

        connectButton = view.findViewById(R.id.btn_connect);
        numDropsRow = view.findViewById(R.id.ll_num_drops);
        numDropsValue = view.findViewById(R.id.tv_num_drops_value);
        loopModeSwitch = view.findViewById(R.id.sw_loop_mode);
        loopOptions = view.findViewById(R.id.ll_loop_options);
        delayMethodGroup = view.findViewById(R.id.tg_delay_method);
        waitSecondsField = view.findViewById(R.id.et_wait_seconds);
        waitSecondsSlider = view.findViewById(R.id.sl_wait_seconds);
        cameraPulseField = view.findViewById(R.id.et_camera_pulse);
        flashPulseField = view.findViewById(R.id.et_flash_pulse);
        cameraPulseChips = view.findViewById(R.id.cg_camera_pulse);
        flashPulseChips = view.findViewById(R.id.cg_flash_pulse);
        cameraChip = view.findViewById(R.id.chip_camera);
        bulbChip = view.findViewById(R.id.chip_bulb);
        flashChips[0] = view.findViewById(R.id.chip_flash1);
        flashChips[1] = view.findViewById(R.id.chip_flash2);
        flashChips[2] = view.findViewById(R.id.chip_flash3);
        // The chip shows an icon; a flash trigger also shows its number
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            flashChips[i].setText(String.valueOf(i + 1));
        }
        soundsSwitch = view.findViewById(R.id.sw_sounds);
        themeGroup = view.findViewById(R.id.tg_theme);

        setupNumDropsRow();
        setupPulseChips(cameraPulseChips, ms -> app.getSettings().setCameraPulseMs(ms));
        setupPulseChips(flashPulseChips, ms -> app.getSettings().setFlashPulseMs(ms));
        setupListeners(view);
        bindViews();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Presets loaded on the Drops or Graph tab may have changed the settings while this tab was hidden
        bindViews();
        bluetoothHelper.addListener(bluetoothListener);
        updateConnectButton(bluetoothHelper.getState());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Avoid a leaked dialog window when the view is re-created (rotation)
        dismissDialog();
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetoothHelper.removeListener(bluetoothListener);
        // A field that still has focus has not been committed yet
        commitNumberFields();
    }

    // ----------------------------------------------------
    // Model <-> views
    // ----------------------------------------------------

    // One drop icon per allowed count; touching the n-th icon (tap or drag) selects n drops
    @SuppressLint("ClickableViewAccessibility")
    private void setupNumDropsRow() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = Limits.MIN_DROPS; i <= Limits.MAX_DROPS; i++) {
            numDropsRow.addView(inflater.inflate(R.layout.item_num_drop, numDropsRow, false));
        }

        numDropsRow.setOnTouchListener((row, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // Keep a horizontal drag away from the parent ScrollView
                row.getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                int count = numDropsAt(event.getX(), row.getWidth());
                if (count != app.getSettings().getNumDrops()) {
                    app.getSettings().setNumDrops(count);
                    showNumDrops(count);
                    row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            if (action == MotionEvent.ACTION_UP) {
                app.saveSettings();
                row.performClick();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                // The model may hold a value picked during the drag; save it so the screen and storage agree
                app.saveSettings();
            }
            return true;
        });
    }

    private static int numDropsAt(float x, int width) {
        int count = Limits.MIN_DROPS + (int) (x / width * (Limits.MAX_DROPS - Limits.MIN_DROPS + 1));
        return Math.max(Limits.MIN_DROPS, Math.min(Limits.MAX_DROPS, count));
    }

    // Fills the first icons with the accent color, dims the rest, and shows the number
    private void showNumDrops(int count) {
        int active = MaterialColors.getColor(numDropsRow, androidx.appcompat.R.attr.colorPrimary, Color.BLUE);
        int inactive = MaterialColors.getColor(numDropsRow,
                com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY);
        for (int i = 0; i < numDropsRow.getChildCount(); i++) {
            ImageView icon = (ImageView) numDropsRow.getChildAt(i);
            icon.setColorFilter(i < count ? active : inactive);
        }
        numDropsValue.setText(String.valueOf(count));
    }

    /**
     * Fills every view from the shared model.
     */
    private void bindViews() {
        AppSettings settings = app.getSettings();
        SequenceData sequence = app.getSequence();

        binding = true;
        showNumDrops(settings.getNumDrops());
        loopModeSwitch.setChecked(settings.isLoopModeOn());
        showLoopOptionsDimmed(!settings.isLoopModeOn());
        delayMethodGroup.check(settings.getDelayMethod() == LoopDelayMethod.TIMER
                ? R.id.btn_delay_timer : R.id.btn_delay_button);
        showNumberValues(settings);
        cameraChip.setChecked(sequence.getCamera().isEnabled());
        bulbChip.setChecked(sequence.getCamera().isBulb());
        bulbChip.setEnabled(sequence.getCamera().isEnabled());
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            flashChips[i].setChecked(sequence.getFlash(i).isEnabled());
        }
        soundsSwitch.setChecked(settings.isSoundsOn());
        themeGroup.check(themeButtonId(settings.getThemeMode()));
        binding = false;
    }

    private static int themeButtonId(@NonNull ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return R.id.btn_theme_light;
            case DARK:
                return R.id.btn_theme_dark;
            default:
                return R.id.btn_theme_system;
        }
    }

    @NonNull
    private static ThemeMode themeModeOf(int buttonId) {
        if (buttonId == R.id.btn_theme_light) {
            return ThemeMode.LIGHT;
        }
        if (buttonId == R.id.btn_theme_dark) {
            return ThemeMode.DARK;
        }
        return ThemeMode.SYSTEM;
    }

    private void setupListeners(@NonNull View view) {
        connectButton.setOnClickListener(v -> onConnectButtonClick());


        loopModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                app.getSettings().setLoopModeOn(checked);
                app.saveSettings();
                showLoopOptionsDimmed(!checked);
            }
        });

        delayMethodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!binding && isChecked) {
                app.getSettings().setDelayMethod(checkedId == R.id.btn_delay_timer
                        ? LoopDelayMethod.TIMER : LoopDelayMethod.BUTTON);
                app.saveSettings();
            }
        });

        waitSecondsSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !binding) {
                // The slider only sets whole seconds; the field follows it
                app.getSettings().setWaitSeconds((int) value);
                app.saveSettings();
                showNumberValues(app.getSettings());
            }
        });

        setupNumberField(waitSecondsField);
        setupNumberField(cameraPulseField);
        setupNumberField(flashPulseField);

        cameraChip.setOnCheckedChangeListener(sequenceSwitchListener(checked -> {
            app.getSequence().getCamera().setEnabled(checked);
            // Bulb only applies to an enabled camera trigger; its value is kept while the camera is OFF
            bulbChip.setEnabled(checked);
        }));
        bulbChip.setOnCheckedChangeListener(sequenceSwitchListener(
                checked -> app.getSequence().getCamera().setBulb(checked)));
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            final int index = i;
            flashChips[i].setOnCheckedChangeListener(sequenceSwitchListener(
                    checked -> app.getSequence().getFlash(index).setEnabled(checked)));
        }

        soundsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                app.getSettings().setSoundsOn(checked);
                app.saveSettings();
            }
        });

        themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!binding && isChecked) {
                app.getSettings().setThemeMode(themeModeOf(checkedId));
                app.saveSettings();
                // Recreates the open activities with the new theme
                app.applyThemeMode();
            }
        });

        view.findViewById(R.id.btn_reset).setOnClickListener(v -> confirmReset());
    }

    // RESET erases all drop data, so it asks first (the original app does not)
    private void confirmReset() {
        dismissDialog();
        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reset_confirm_title)
                .setMessage(R.string.reset_confirm_message)
                .setPositiveButton(R.string.btn_reset, (d, which) -> {
                    app.resetAll();
                    bindViews();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    @NonNull
    private CompoundButton.OnCheckedChangeListener sequenceSwitchListener(@NonNull BooleanSetter setter) {
        return (button, checked) -> {
            if (!binding) {
                setter.set(checked);
                app.saveSequence();
            }
        };
    }

    /**
     * A number field is committed when it loses focus or the keyboard Done key is pressed.
     */
    private void setupNumberField(@NonNull EditText field) {
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitNumberFields();
            }
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus();
                commitNumberFields();
            }
            return false;
        });
    }

    /**
     * Parses the number fields, clamps them via the model setters and shows the stored values back.
     */
    private void commitNumberFields() {
        if (binding || app == null) {
            return;
        }
        AppSettings settings = app.getSettings();
        settings.setWaitSeconds(parseNumber(waitSecondsField, settings.getWaitSeconds()));
        settings.setCameraPulseMs(parseNumber(cameraPulseField, settings.getCameraPulseMs()));
        settings.setFlashPulseMs(parseNumber(flashPulseField, settings.getFlashPulseMs()));
        app.saveSettings();
        showNumberValues(settings);
    }

    /**
     * Shows the stored numbers in the fields and keeps the slider and the quick value chips in step with them.
     */
    private void showNumberValues(@NonNull AppSettings settings) {
        boolean wasBinding = binding;
        binding = true;
        waitSecondsField.setText(String.valueOf(settings.getWaitSeconds()));
        cameraPulseField.setText(String.valueOf(settings.getCameraPulseMs()));
        flashPulseField.setText(String.valueOf(settings.getFlashPulseMs()));
        waitSecondsSlider.setValue(Math.min(settings.getWaitSeconds(), SLIDER_MAX_WAIT_SECONDS));
        markQuickPulse(cameraPulseChips, settings.getCameraPulseMs());
        markQuickPulse(flashPulseChips, settings.getFlashPulseMs());
        binding = wasBinding;
    }

    // Inflates one chip per quick value; a tap sets the pulse time and shows it in the field
    private void setupPulseChips(@NonNull ChipGroup group, @NonNull IntConsumer setter) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int ms : QUICK_PULSE_MS) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_pulse_chip, group, false);
            chip.setText(getString(R.string.settings_pulse_chip, ms));
            chip.setTag(ms);
            chip.setOnClickListener(v -> {
                setter.accept(ms);
                app.saveSettings();
                // Also puts the chip that the tap just toggled back in step with the value
                showNumberValues(app.getSettings());
            });
            group.addView(chip);
        }
    }

    // Checks the chip that equals the current value (none for a custom value)
    private static void markQuickPulse(@NonNull ChipGroup group, int valueMs) {
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            chip.setChecked((Integer) chip.getTag() == valueMs);
        }
    }

    // The options stay editable, only their look shows that they do not apply
    private void showLoopOptionsDimmed(boolean dimmed) {
        loopOptions.setAlpha(dimmed ? DIMMED_ALPHA : 1f);
    }

    private static int parseNumber(@NonNull EditText field, int fallback) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            // Empty or invalid input keeps the previous value
            return fallback;
        }
    }

    // ----------------------------------------------------
    // Connection (Analogous to ListPicker.Elements and ListPicker.Show())
    // ----------------------------------------------------

    private void updateConnectButton(@NonNull BluetoothHelper.State state) {
        switch (state) {
            case CONNECTING:
                connectButton.setText(R.string.btn_connecting);
                break;
            case CONNECTED:
                connectButton.setText(R.string.btn_connected);
                break;
            default:
                connectButton.setText(R.string.btn_not_connected);
                break;
        }
    }

    private void onConnectButtonClick() {
        if (bluetoothHelper.getState() != BluetoothHelper.State.DISCONNECTED) {
            bluetoothHelper.disconnect();
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(requireContext(), R.string.bt_no_paired_devices, Toast.LENGTH_LONG).show();
            return;
        }

        final List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        final List<String> deviceLabels = new ArrayList<>();
        for (BluetoothDevice device : deviceList) {
            deviceLabels.add(bluetoothHelper.getDeviceLabel(device));
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(requireContext(), android.R.layout.select_dialog_item, deviceLabels);

        dismissDialog();
        dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bt_select_device)
                // Runs when a device is selected (Analogous to ListPicker.AfterPicking)
                .setAdapter(adapter, (dialog, which) -> bluetoothHelper.connect(deviceList.get(which)))
                .setNegativeButton(R.string.bt_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }
}
