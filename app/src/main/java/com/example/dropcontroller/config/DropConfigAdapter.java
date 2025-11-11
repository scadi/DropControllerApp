package com.example.dropcontroller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dropcontroller.data.DropData;

import java.util.List;

// Extends the standard RecyclerView Adapter and uses our custom ViewHolder
public class DropConfigAdapter extends RecyclerView.Adapter<DropConfigAdapter.DropViewHolder> {

    private List<DropData> dropDataList;
    // Interface to handle button clicks (like App Inventor's 'when Button.Click')
    private OnDropConfigListener listener;

    // Constructor
    public DropConfigAdapter(List<DropData> dropDataList, OnDropConfigListener listener) {
        this.dropDataList = dropDataList;
        this.listener = listener;
    }

    // ----------------------------------------------------
    // 1. ViewHolder: Holds the views for one list item (D1)
    // ----------------------------------------------------
    public static class DropViewHolder extends RecyclerView.ViewHolder {
        public TextView dropHeader;
        public EditText solenoidHz;
        public TextView sliderLabel;
        public SeekBar dropSizeSeekBar;
        public Button sendButton;

        public DropViewHolder(View itemView) {
            super(itemView);
            // Binding UI elements from the item_drop_configuration.xml template
            dropHeader = itemView.findViewById(R.id.tv_drop_header);
            solenoidHz = itemView.findViewById(R.id.et_solenoid_hz);
            sliderLabel = itemView.findViewById(R.id.tv_slider_label);
            dropSizeSeekBar = itemView.findViewById(R.id.sb_drop_size);
            sendButton = itemView.findViewById(R.id.btn_send_drop_config);
        }
    }

    // ----------------------------------------------------
    // 2. Adapter Methods
    // ----------------------------------------------------

    // Creates the ViewHolder by 'inflating' the XML layout
    @NonNull
    @Override
    public DropViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drop_configuration, parent, false);
        return new DropViewHolder(view);
    }

    // Binds the data to the views (This is called 9 times for D1-D9)
    @Override
    public void onBindViewHolder(@NonNull DropViewHolder holder, int position) {
        DropData currentDrop = dropDataList.get(position);

        // Set the text from the data model
        holder.dropHeader.setText("Drop D" + currentDrop.getDropNumber() + " Configuration");
        holder.solenoidHz.setText(currentDrop.getSolenoidFrequency());
        holder.sliderLabel.setText("Drop Size: (" + currentDrop.getSliderPosition() + ")");
        holder.dropSizeSeekBar.setProgress(currentDrop.getSliderPosition());
        holder.sendButton.setText("SEND D" + currentDrop.getDropNumber() + " CONFIG");

        // Handle Slider change events (Analogous to when Slider.PositionChanged)
        holder.dropSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update the model and the label text immediately
                currentDrop.setSliderPosition(progress);
                holder.sliderLabel.setText("Drop Size: (" + progress + ")");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* Not used */ }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional: Notify the Fragment when the user stops dragging
            }
        });

        // Handle button click (Analogous to when D1_SEND_BTN.Click)
        holder.sendButton.setOnClickListener(v -> {
            // Update the model from EditText before sending
            currentDrop.setSolenoidFrequency(holder.solenoidHz.getText().toString());
            // Call the interface method defined in the Fragment
            listener.onSendConfigClick(currentDrop);
        });
    }

    // Returns the total number of items (must be 9 for D1-D9)
    @Override
    public int getItemCount() {
        return dropDataList.size();
    }

    // Interface definition for communication back to the Fragment
    public interface OnDropConfigListener {
        void onSendConfigClick(DropData dropData);
    }
}