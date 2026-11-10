package com.example.dropcontroller.fragments;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.DropControllerApp;
import com.example.dropcontroller.R;
import com.example.dropcontroller.services.BluetoothHelper;
import com.example.dropcontroller.services.DeviceSession;

/**
 * About tab: the app version and, once connected, the firmware version reported by the device ([VERSION] reply).
 */
public class AboutFragment extends Fragment {

    private DeviceSession deviceSession;
    private BluetoothHelper bluetoothHelper;
    private TextView firmwareView;

    private final DeviceSession.Listener sessionListener = (version, compileDate) -> updateFirmware();

    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            updateFirmware();
        }
    };

    public AboutFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);
        DropControllerApp app = (DropControllerApp) requireActivity().getApplication();
        deviceSession = app.getDeviceSession();
        bluetoothHelper = app.getBluetoothHelper();

        TextView appVersionView = view.findViewById(R.id.tv_app_version);
        appVersionView.setText(getString(R.string.about_app_version, getAppVersion()));
        firmwareView = view.findViewById(R.id.tv_firmware_version);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        deviceSession.addListener(sessionListener);
        bluetoothHelper.addListener(bluetoothListener);
        updateFirmware();
    }

    @Override
    public void onPause() {
        super.onPause();
        deviceSession.removeListener(sessionListener);
        bluetoothHelper.removeListener(bluetoothListener);
    }

    private void updateFirmware() {
        if (!isAdded()) {
            return;
        }
        String version = deviceSession.getFirmwareVersion();
        String date = deviceSession.getCompileDate();
        if (version != null) {
            if (date == null || date.isEmpty()) {
                firmwareView.setText(getString(R.string.about_firmware_no_date, version));
            } else {
                firmwareView.setText(getString(R.string.about_firmware, version, date));
            }
        } else if (bluetoothHelper.isConnected()) {
            firmwareView.setText(R.string.about_firmware_waiting);
        } else {
            firmwareView.setText(R.string.about_firmware_unknown);
        }
    }

    @NonNull
    private String getAppVersion() {
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return info.versionName != null ? info.versionName : "";
        } catch (PackageManager.NameNotFoundException e) {
            // Cannot happen for the own package; show an empty version
            return "";
        }
    }
}
