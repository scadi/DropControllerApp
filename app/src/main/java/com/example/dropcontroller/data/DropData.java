package com.example.dropcontroller.data;

// This class will hold all the data for a single drop configuration (e.g., D1)
public class DropData {
    private int dropNumber; // e.g., 1 for D1, 2 for D2
    private String solenoidFrequency; // Analogous to D1_SOL_HZ.Text
    private String startTime;         // Analogous to D1_START_HZ.Text
    private String sizeValue;         // Analogous to D1_SIZE_HZ.Text
    private int sliderPosition;       // Value of the Slider component

    // Constructor to initialize the data
    public DropData(int dropNumber, String solenoidFrequency, String startTime, String sizeValue, int sliderPosition) {
        this.dropNumber = dropNumber;
        this.solenoidFrequency = solenoidFrequency;
        this.startTime = startTime;
        this.sizeValue = sizeValue;
        this.sliderPosition = sliderPosition;
    }

    // --- Getters (Methods to read the data) ---

    public int getDropNumber() {
        return dropNumber;
    }

    public String getSolenoidFrequency() {
        return solenoidFrequency;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getSizeValue() {
        return sizeValue;
    }

    public int getSliderPosition() {
        return sliderPosition;
    }

    // --- Setters (Methods to change the data) ---

    public void setSolenoidFrequency(String solenoidFrequency) {
        this.solenoidFrequency = solenoidFrequency;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public void setSizeValue(String sizeValue) {
        this.sizeValue = sizeValue;
    }

    public void setSliderPosition(int sliderPosition) {
        this.sliderPosition = sliderPosition;
    }
}