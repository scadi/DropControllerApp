package com.example.dropcontroller.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.R;
import com.example.dropcontroller.data.Limits;
import com.example.dropcontroller.logic.ProtocolEncoder;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Drain tab: opens and closes a valve manually ([O&lt;v&gt;1] / [O&lt;v&gt;0]).
 * <p>
 * The valve state lives in the device, not in the app, so all valves are closed ([OX]) when the tab is left. That
 * keeps the switches and the real valves in step after the fragment is recreated.
 */
public class DrainFragment extends Fragment {

    private BluetoothHelper bluetoothHelper;
    private final MaterialSwitch[] valveSwitches = new MaterialSwitch[Limits.MAX_SOLENOID];
    private Button closeAllButton;

    // True while the switches are changed by the code, so the change listeners do not send anything
    private boolean binding;

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            if (state != BluetoothHelper.State.CONNECTED) {
                // The device closes nothing by itself, but the app can no longer control the valves
                setAllSwitches(false);
            }
            updateEnabled();
        }

        @Override
        public void onError(int messageResId) {
            if (isAdded()) {
                Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show();
            }
        }
    };

    public DrainFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drain, container, false);
        bluetoothHelper = ((DropControllerApp) requireActivity().getApplication()).getBluetoothHelper();

        LinearLayout valveList = view.findViewById(R.id.ll_valves);
        for (int i = 0; i < Limits.MAX_SOLENOID; i++) {
            final int valve = i + Limits.MIN_SOLENOID;
            MaterialSwitch valveSwitch = new MaterialSwitch(requireContext());
            valveSwitch.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            valveSwitch.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            valveSwitch.setText(getString(R.string.drain_valve, valve));
            valveSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (!binding) {
                    onValveToggled(valve, checked);
                }
            });
            valveList.addView(valveSwitch);
            valveSwitches[i] = valveSwitch;
        }

        closeAllButton = view.findViewById(R.id.btn_close_all);
        closeAllButton.setOnClickListener(v -> closeAll());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        bluetoothHelper.addListener(bluetoothListener);
        setAllSwitches(false);
        updateEnabled();
    }

    @Override
    public void onPause() {
        super.onPause();
        bluetoothHelper.removeListener(bluetoothListener);
        if (bluetoothHelper.isConnected()) {
            bluetoothHelper.send(ProtocolEncoder.allValvesClose());
        }
    }

    private void onValveToggled(int valve, boolean open) {
        String message = open ? ProtocolEncoder.valveOpen(valve) : ProtocolEncoder.valveClose(valve);
        if (!bluetoothHelper.send(message)) {
            // Not connected: the error was reported by the helper, put the switch back
            setSwitch(valve - Limits.MIN_SOLENOID, !open);
        }
    }

    private void closeAll() {
        if (bluetoothHelper.send(ProtocolEncoder.allValvesClose())) {
            setAllSwitches(false);
        }
    }

    private void setSwitch(int index, boolean checked) {
        binding = true;
        valveSwitches[index].setChecked(checked);
        binding = false;
    }

    private void setAllSwitches(boolean checked) {
        for (int i = 0; i < valveSwitches.length; i++) {
            setSwitch(i, checked);
        }
    }

    private void updateEnabled() {
        boolean connected = bluetoothHelper.isConnected();
        for (MaterialSwitch valveSwitch : valveSwitches) {
            valveSwitch.setEnabled(connected);
        }
        closeAllButton.setEnabled(connected);
    }
}
