package com.example.dropcontroller.fragments;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.R;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.LoopDelayMethod;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Settings tab: connection, number of drops, Loop Mode, trigger pulse times, trigger enable switches,
 * sounds and RESET. Changes are applied to the shared {@link AppSettings} / {@link SequenceData} and saved at once.
 */
public class SettingsFragment extends Fragment {

    private DropControllerApp app;
    private BluetoothHelper bluetoothHelper;

    private Button connectButton;
    private Spinner numDropsSpinner;
    private MaterialSwitch loopModeSwitch;
    private MaterialButtonToggleGroup delayMethodGroup;
    private EditText waitSecondsField;
    private EditText cameraPulseField;
    private EditText flashPulseField;
    private MaterialSwitch cameraSwitch;
    private final MaterialSwitch[] flashSwitches = new MaterialSwitch[SequenceData.FLASH_COUNT];
    private MaterialSwitch soundsSwitch;

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
        numDropsSpinner = view.findViewById(R.id.sp_num_drops);
        loopModeSwitch = view.findViewById(R.id.sw_loop_mode);
        delayMethodGroup = view.findViewById(R.id.tg_delay_method);
        waitSecondsField = view.findViewById(R.id.et_wait_seconds);
        cameraPulseField = view.findViewById(R.id.et_camera_pulse);
        flashPulseField = view.findViewById(R.id.et_flash_pulse);
        cameraSwitch = view.findViewById(R.id.sw_camera);
        flashSwitches[0] = view.findViewById(R.id.sw_flash1);
        flashSwitches[1] = view.findViewById(R.id.sw_flash2);
        flashSwitches[2] = view.findViewById(R.id.sw_flash3);
        soundsSwitch = view.findViewById(R.id.sw_sounds);

        setupNumDropsSpinner();
        setupListeners(view);
        bindViews();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        bluetoothHelper.addListener(bluetoothListener);
        updateConnectButton(bluetoothHelper.getState());
    }

    @Override
    public void onStop() {
        super.onStop();
        bluetoothHelper.removeListener(bluetoothListener);
        // A field that still has focus has not been committed yet
        commitNumberFields();
    }

    // ----------------------------------------------------
    // Model <-> views
    // ----------------------------------------------------

    private void setupNumDropsSpinner() {
        List<String> values = new ArrayList<>();
        for (int i = Limits.MIN_DROPS; i <= Limits.MAX_DROPS; i++) {
            values.add(String.valueOf(i));
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        numDropsSpinner.setAdapter(adapter);
    }

    /**
     * Fills every view from the shared model.
     */
    private void bindViews() {
        AppSettings settings = app.getSettings();
        SequenceData sequence = app.getSequence();

        binding = true;
        numDropsSpinner.setSelection(settings.getNumDrops() - Limits.MIN_DROPS);
        loopModeSwitch.setChecked(settings.isLoopModeOn());
        delayMethodGroup.check(settings.getDelayMethod() == LoopDelayMethod.TIMER
                ? R.id.btn_delay_timer : R.id.btn_delay_button);
        waitSecondsField.setText(String.valueOf(settings.getWaitSeconds()));
        cameraPulseField.setText(String.valueOf(settings.getCameraPulseMs()));
        flashPulseField.setText(String.valueOf(settings.getFlashPulseMs()));
        cameraSwitch.setChecked(sequence.getCamera().isEnabled());
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            flashSwitches[i].setChecked(sequence.getFlash(i).isEnabled());
        }
        soundsSwitch.setChecked(settings.isSoundsOn());
        binding = false;
    }

    private void setupListeners(@NonNull View view) {
        connectButton.setOnClickListener(v -> onConnectButtonClick());

        numDropsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                if (!binding) {
                    app.getSettings().setNumDrops(position + Limits.MIN_DROPS);
                    app.saveSettings();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Nothing to do
            }
        });

        loopModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                app.getSettings().setLoopModeOn(checked);
                app.saveSettings();
            }
        });

        delayMethodGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!binding && isChecked) {
                app.getSettings().setDelayMethod(checkedId == R.id.btn_delay_timer
                        ? LoopDelayMethod.TIMER : LoopDelayMethod.BUTTON);
                app.saveSettings();
            }
        });

        setupNumberField(waitSecondsField);
        setupNumberField(cameraPulseField);
        setupNumberField(flashPulseField);

        cameraSwitch.setOnCheckedChangeListener(sequenceSwitchListener(
                checked -> app.getSequence().getCamera().setEnabled(checked)));
        for (int i = 0; i < SequenceData.FLASH_COUNT; i++) {
            final int index = i;
            flashSwitches[i].setOnCheckedChangeListener(sequenceSwitchListener(
                    checked -> app.getSequence().getFlash(index).setEnabled(checked)));
        }

        soundsSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                app.getSettings().setSoundsOn(checked);
                app.saveSettings();
            }
        });

        view.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            app.resetAll();
            bindViews();
        });
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

        binding = true;
        waitSecondsField.setText(String.valueOf(settings.getWaitSeconds()));
        cameraPulseField.setText(String.valueOf(settings.getCameraPulseMs()));
        flashPulseField.setText(String.valueOf(settings.getFlashPulseMs()));
        binding = false;
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

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.bt_select_device)
                // Runs when a device is selected (Analogous to ListPicker.AfterPicking)
                .setAdapter(adapter, (dialog, which) -> bluetoothHelper.connect(deviceList.get(which)))
                .setNegativeButton(R.string.bt_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }
}
