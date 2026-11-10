package com.example.dropcontroller;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.example.dropcontroller.fragments.AboutFragment;
import com.example.dropcontroller.fragments.DrainFragment;
import com.example.dropcontroller.fragments.DropsFragment;
import com.example.dropcontroller.fragments.GraphFragment;
import com.example.dropcontroller.fragments.SettingsFragment;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

// Handles tab selection through BottomNavigationView.OnNavigationItemSelectedListener
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private View splashContainer;
    private Button splashButton;
    private RelativeLayout mainContentContainer;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigationView;
    private static final String STATE_MAIN_SHOWN = "main_shown";
    private final int REQUEST_BLUETOOTH_PERMISSIONS = 100; // Constant request code

    private BluetoothHelper bluetoothHelper;
    private final BluetoothHelper.Listener bluetoothListener = new BluetoothHelper.Listener() {
        @Override
        public void onStateChanged(@NonNull BluetoothHelper.State state) {
            updateSettingsTabLabel();
            updateConnectionIndicator();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialization of views
        splashContainer = findViewById(R.id.splash_container);
        splashButton = findViewById(R.id.btn_splash_screen);
        mainContentContainer = findViewById(R.id.main_content_container);
        toolbar = findViewById(R.id.toolbar);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bluetoothHelper = ((DropControllerApp) getApplication()).getBluetoothHelper();

        toolbar.inflateMenu(R.menu.top_app_bar_menu);


        // 2. Splash screen button click listener
        splashButton.setOnClickListener(v -> {
            showMainContent();

            // Load the first fragment (DropsFragment) on start
            loadFragment(new DropsFragment());
        });

        // After a rotation the activity is recreated: skip the splash screen. The fragment manager restores the
        // current fragment and the bottom navigation restores the selected item by itself.
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_MAIN_SHOWN, false)) {
            showMainContent();
        }

        // Call the method to check permissions immediately after creating the Activity
        checkAndRequestBluetoothPermissions();

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_MAIN_SHOWN, mainContentContainer.getVisibility() == View.VISIBLE);
    }

    // Hides the splash screen and enables the tab navigation
    private void showMainContent() {
        splashContainer.setVisibility(View.GONE);
        mainContentContainer.setVisibility(View.VISIBLE);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
        updateToolbarTitle(bottomNavigationView.getSelectedItemId());
    }

    // The top app bar shows the name of the current tab
    private void updateToolbarTitle(int navItemId) {
        int titleRes;
        if (navItemId == R.id.nav_settings) {
            titleRes = R.string.nav_settings;
        } else if (navItemId == R.id.nav_graph) {
            titleRes = R.string.nav_graph;
        } else if (navItemId == R.id.nav_drain) {
            titleRes = R.string.nav_drain;
        } else if (navItemId == R.id.nav_about) {
            titleRes = R.string.nav_about;
        } else {
            titleRes = R.string.nav_drops;
        }
        toolbar.setTitle(titleRes);
    }

    // Shows the Bluetooth state in the top app bar: icon tint, tooltip and subtitle
    private void updateConnectionIndicator() {
        MenuItem item = toolbar.getMenu().findItem(R.id.action_connection);
        int statusRes;
        int colorAttr;
        switch (bluetoothHelper.getState()) {
            case CONNECTED:
                statusRes = R.string.status_connected;
                colorAttr = androidx.appcompat.R.attr.colorPrimary;
                break;
            case CONNECTING:
                statusRes = R.string.status_connecting;
                colorAttr = com.google.android.material.R.attr.colorTertiary;
                break;
            default:
                statusRes = R.string.status_not_connected;
                colorAttr = com.google.android.material.R.attr.colorOutline;
                break;
        }
        toolbar.setSubtitle(statusRes);
        if (item != null) {
            item.setTitle(statusRes);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon = DrawableCompat.wrap(icon.mutate());
                DrawableCompat.setTint(icon, MaterialColors.getColor(toolbar, colorAttr));
                item.setIcon(icon);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bluetoothHelper.addListener(bluetoothListener);
        updateSettingsTabLabel();
        updateConnectionIndicator();
    }

    @Override
    protected void onStop() {
        super.onStop();
        bluetoothHelper.removeListener(bluetoothListener);
    }

    // The Settings tab label is blue while a device is connected
    private void updateSettingsTabLabel() {
        MenuItem settingsItem = bottomNavigationView.getMenu().findItem(R.id.nav_settings);
        if (settingsItem == null) {
            return;
        }
        String title = getString(R.string.nav_settings);
        if (bluetoothHelper.isConnected()) {
            SpannableString colored = new SpannableString(title);
            colored.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.nav_connected)),
                    0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            settingsItem.setTitle(colored);
        } else {
            settingsItem.setTitle(title);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the connection only when the app is really finishing (not on rotation)
        if (isFinishing()) {
            ((DropControllerApp) getApplication()).getBluetoothHelper().disconnect();
        }
    }

    // Loads a fragment into the container
    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment) // Replace the current fragment in the container
                    .commit(); // Commit the transaction
            return true;
        }
        return false;
    }

    // 4. Menu item click handler
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Fragment fragment = null;

        // R.id.nav_drops, R.id.nav_settings etc. are IDs from bottom_nav_menu.xml
        int itemId = item.getItemId();
        if (itemId == R.id.nav_drops) {
            fragment = new DropsFragment();
        } else if (itemId == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else if (itemId == R.id.nav_graph) {
            fragment = new GraphFragment();
        } else if (itemId == R.id.nav_drain) {
            fragment = new DrainFragment();
        } else if (itemId == R.id.nav_about) {
            fragment = new AboutFragment();
        }

        boolean loaded = loadFragment(fragment);
        if (loaded) {
            updateToolbarTitle(itemId);
        }
        return loaded;
    }

    // New method to handle permission requests
    private void checkAndRequestBluetoothPermissions() {
        // List of permissions we need to check
        String[] permissions;

        // Check Android version to determine required permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31) and above: BLUETOOTH_SCAN and BLUETOOTH_CONNECT
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            // Older Android versions: ACCESS_FINE_LOCATION is often required for scanning
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS
            );
        } else {
            // Permissions are already granted, proceed with Bluetooth operations
            // This is where you might enable the splash screen button or start the app logic
        }
    }

    // Callback method for the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // All required permissions were granted
                // You can proceed with Bluetooth initialization now
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show();
            } else {
                // User denied one or more permissions
                Toast.makeText(this, R.string.permissions_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

}