package com.example.dropcontroller.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.LoopActivity;
import com.example.dropcontroller.R;
import com.example.dropcontroller.data.AppSettings;
import com.example.dropcontroller.data.CameraTrigger;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.data.FlashTrigger;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.data.SequenceData;
import com.example.dropcontroller.services.BluetoothHelper;
import com.example.dropcontroller.services.SequenceRunner;
import com.example.dropcontroller.views.ValueDeltaRow;
import com.google.android.material.materialswitch.MaterialSwitch;

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

    private DropControllerApp app;
    private BluetoothHelper bluetoothHelper;
    private SequenceRunner sequenceRunner;

    private final List<View> dropViews = new ArrayList<>();
    private final List<DropRow> dropRows = new ArrayList<>();
    private ValueDeltaRow cameraRow;
    private final ValueDeltaRow[] flashRows = new ValueDeltaRow[SequenceData.FLASH_COUNT];
    private MaterialSwitch bulbSwitch;
    private View triggersCard;
    private Button startButton;

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

        LinearLayout dropList = view.findViewById(R.id.ll_drops);
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
            View item = inflater.inflate(R.layout.item_drop, dropList, false);
            dropList.addView(item);
            dropViews.add(item);
            dropRows.add(createDropRow(item, i, sequence.getDrop(i)));
        }

        createTriggerRows(view, sequence);

        startButton = view.findViewById(R.id.btn_start);
        startButton.setOnClickListener(v -> onStartClick());

        bindViews();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        bluetoothHelper.addListener(bluetoothListener);
        sequenceRunner.addListener(sequenceListener);
        // Settings may have changed on the Settings tab
        bindViews();
    }

    @Override
    public void onStop() {
        super.onStop();
        bluetoothHelper.removeListener(bluetoothListener);
        sequenceRunner.removeListener(sequenceListener);
        // A field that still has focus has not been committed yet
        commitAll();
    }

    // ----------------------------------------------------
    // Building the rows
    // ----------------------------------------------------

    @NonNull
    private DropRow createDropRow(@NonNull View item, int index, @NonNull DropData drop) {
        ((TextView) item.findViewById(R.id.tv_drop_title)).setText(getString(R.string.drop_title, index + 1));

        Spinner solenoid = item.findViewById(R.id.sp_solenoid);
        solenoid.setSaveEnabled(false);
        List<String> values = new ArrayList<>();
        for (int sol = Limits.MIN_SOLENOID; sol <= Limits.MAX_SOLENOID; sol++) {
            values.add(String.valueOf(sol));
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, values);
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
        cameraRow.setLabel(R.string.trigger_camera);

        bulbSwitch = view.findViewById(R.id.sw_bulb);
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
            flashRows[i].setLabel(R.string.trigger_flash, i + 1);
        }
    }

    // ----------------------------------------------------
    // Model <-> views
    // ----------------------------------------------------

    private void bindViews() {
        AppSettings settings = app.getSettings();
        SequenceData sequence = app.getSequence();

        binding = true;
        for (int i = 0; i < Limits.MAX_DROPS; i++) {
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
        for (int i = 0; i < app.getSettings().getNumDrops(); i++) {
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

        int colorRes;
        if (active) {
            colorRes = R.color.start_active;
            startButton.setText(R.string.btn_start_active);
        } else {
            colorRes = connected ? R.color.start_ready : R.color.start_inactive;
            startButton.setText(R.string.btn_start);
        }
        startButton.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes)));
        startButton.setEnabled(connected && !active);
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
}
