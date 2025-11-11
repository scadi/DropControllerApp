package com.example.dropcontroller.services;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.dropcontroller.DropConfigAdapter;
import com.example.dropcontroller.R;
import com.example.dropcontroller.data.DropData;

import java.util.ArrayList;
import java.util.List;

// DropsFragment must implement the Adapter's listener interface
public class DropsFragment extends Fragment implements DropConfigAdapter.OnDropConfigListener {

    private RecyclerView recyclerView;
    private DropConfigAdapter adapter;
    private List<DropData> dropDataList;

    public DropsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drops, container, false);

        // 1. Initialize the RecyclerView
        recyclerView = view.findViewById(R.id.rv_drop_configs);

        // 2. Create the dummy data list for D1 to D9
        dropDataList = generateDummyDropData(9);

        // 3. Create the adapter, passing the data list and 'this' (the listener)
        adapter = new DropConfigAdapter(dropDataList, this);

        // 4. Set the layout manager (defines how items are arranged: linear, grid, etc.)
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 5. Connect the adapter to the RecyclerView
        recyclerView.setAdapter(adapter);

        return view;
    }

    // Helper method to generate the 9 drop sections
    private List<DropData> generateDummyDropData(int count) {
        List<DropData> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            // Initial dummy values
            list.add(new DropData(
                    i,
                    "100", // Initial Solenoid Frequency
                    "0",   // Initial Start Time
                    "50",  // Initial Size Value
                    50     // Initial Slider Position
            ));
        }
        return list;
    }

    // ----------------------------------------------------
    // Implementation of the OnDropConfigListener interface
    // ----------------------------------------------------

    // This method is called when the SEND button is clicked on any D1-D9 item
    @Override
    public void onSendConfigClick(DropData dropData) {
        // Logics from App Inventor blocks (e.g., Send data via Bluetooth) go here.

        // For now, we show a success message:
        String message = "Sending config for D" + dropData.getDropNumber() +
                ": Freq=" + dropData.getSolenoidFrequency() +
                ", Size=" + dropData.getSliderPosition();

        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();

        // **TODO: Next lesson will cover how to send this data via Bluetooth**
    }
}