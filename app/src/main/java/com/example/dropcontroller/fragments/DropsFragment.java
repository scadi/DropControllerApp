package com.example.dropcontroller.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.dropcontroller.DropConfigAdapter;
import com.example.dropcontroller.R;
import com.example.dropcontroller.data.DropData;
import com.example.dropcontroller.services.BluetoothHelper;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link DropsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class DropsFragment extends Fragment implements DropConfigAdapter.OnDropConfigListener {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private RecyclerView recyclerView;
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private BluetoothHelper bluetoothHelper; // New
    private Button connectButton;           // New
    private TextView statusTextView;        // New

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public DropsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment DropsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static DropsFragment newInstance(String param1, String param2) {
        DropsFragment fragment = new DropsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drops, container, false);

        // 1. Initialize Bluetooth Helper
        bluetoothHelper = new BluetoothHelper(getContext());

        // 2. Initialize UI elements
        connectButton = view.findViewById(R.id.btn_connect_bluetooth);
        statusTextView = view.findViewById(R.id.tv_connection_status);

        // 3. Setup Connection Logic
        setupConnectionButton();

        // 4. Initialize RecyclerView (as before)
        recyclerView = view.findViewById(R.id.rv_drop_configs);
        // ... (rest of RecyclerView setup) ...

        return view;
    }
    // ----------------------------------------------------
    // Update the onSendConfigClick method (Data Transfer)
    // ----------------------------------------------------

    @Override
    public void onSendConfigClick(DropData dropData) {
        // App Inventor logic: Create the message string
        // Example format: "D1,100,50" (Drop ID, Frequency, Size)
        String message = String.format("D%d,F%s,S%d\n",
                dropData.getDropNumber(),
                dropData.getSolenoidFrequency(),
                dropData.getSliderPosition());

        // Send the data via Bluetooth
        bluetoothHelper.write(message);

        Toast.makeText(getContext(), "Sent: " + message.trim(), Toast.LENGTH_SHORT).show();
    }

    // Remember to call disconnect() when the Fragment is destroyed!
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothHelper != null) {
            bluetoothHelper.disconnect();
        }
    }

    // New method to handle the connect button logic
    private void setupConnectionButton() {
        connectButton.setOnClickListener(v -> {
            Set<BluetoothDevice> pairedDevices = bluetoothHelper.getPairedDevices();

            if (pairedDevices == null || pairedDevices.isEmpty()) {
                Toast.makeText(getContext(), "No paired devices found. Pair them in Android Settings.", Toast.LENGTH_LONG).show();
                return;
            }

            // Analogous to ListPicker.Elements and ListPicker.Show()
            final List<String> deviceNames = new ArrayList<>();
            final List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);

            for (BluetoothDevice device : deviceList) {
                // We use the @SuppressLint because we checked for permissions in MainActivity
                @SuppressLint("MissingPermission")
                String name = device.getName() != null ? device.getName() : "Unknown Device";
                deviceNames.add(name + "\n" + device.getAddress());
            }

            // Create a dialog similar to ListPicker
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Select Bluetooth Device");

            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), android.R.layout.select_dialog_item, deviceNames);

            builder.setAdapter(arrayAdapter, (dialog, which) -> {
                // This runs when a device is selected (Analogous to when ListPicker.AfterPicking)
                BluetoothDevice selectedDevice = deviceList.get(which);

                // Start connection attempt
                bluetoothHelper.connect(selectedDevice);
                statusTextView.setText("Status: Connecting to " + selectedDevice.getAddress() + "...");
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.show();
        });
    }
}