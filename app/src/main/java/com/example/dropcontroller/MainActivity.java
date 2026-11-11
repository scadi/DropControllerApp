package com.example.dropcontroller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.example.dropcontroller.fragments.AboutFragment;
import com.example.dropcontroller.fragments.DrainFragment;
import com.example.dropcontroller.fragments.DropsFragment;
import com.example.dropcontroller.fragments.GraphFragment;
import com.example.dropcontroller.fragments.SettingsFragment;
import com.example.dropcontroller.logic.ProtocolFileLog;
import com.example.dropcontroller.logic.ProtocolLog;
import com.example.dropcontroller.services.BluetoothHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Handles tab selection through NavigationBarView.OnItemSelectedListener
public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private View splashContainer;
    private Button splashButton;
    private RelativeLayout mainContentContainer;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigationView;
    private static final String STATE_MAIN_SHOWN = "main_shown";
    private final int REQUEST_BLUETOOTH_PERMISSIONS = 100; // Constant request code

    private BluetoothHelper bluetoothHelper;
    @Nullable
    private AlertDialog protocolLogDialog;
    @Nullable
    private AlertDialog communicationLogDialog;
    // The communication log file is read and cleared here, so a slow filesystem does not stall the UI thread
    private final ExecutorService logIoExecutor = Executors.newSingleThreadExecutor();
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
        if (!((DropControllerApp) getApplication()).isDebuggable()) {
            toolbar.getMenu().removeItem(R.id.action_protocol_log);
        }
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemSelected);


        // 2. Splash screen button click listener
        splashButton.setOnClickListener(v -> {
            showMainContent();

            // Show the first tab (Drops) on start
            showTab(R.id.nav_drops);
        });

        // After a rotation the activity is recreated: skip the splash screen. The fragment manager restores the
        // current fragment and the bottom navigation restores the selected item by itself.
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_MAIN_SHOWN, false)) {
            showMainContent();
            limitHiddenTabs();
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
        bottomNavigationView.setOnItemSelectedListener(this);
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

    // Debug-only overflow item: shows the messages exchanged with the device
    private boolean onToolbarMenuItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_protocol_log) {
            showProtocolLog();
            return true;
        }
        return false;
    }

    /**
     * Shows the protocol log in a dialog that updates while it is open. The log is filled by BluetoothHelper, so
     * it also shows the messages of the built-in simulator. Opened from the debug-only overflow menu item.
     */
    public void showProtocolLog() {
        if (protocolLogDialog != null && protocolLogDialog.isShowing()) {
            return;
        }
        ProtocolLog protocolLog = bluetoothHelper.getProtocolLog();
        View content = getLayoutInflater().inflate(R.layout.dialog_protocol_log, null);
        TextView textView = content.findViewById(R.id.tv_protocol_log);
        ScrollView scrollView = content.findViewById(R.id.scroll_protocol_log);

        renderProtocolLog(textView, protocolLog);
        ProtocolLog.Listener listener = () -> runOnUiThread(() -> {
            boolean wasAtBottom = isScrolledToBottom(scrollView);
            renderProtocolLog(textView, protocolLog);
            // Do not fight the user while they read older entries
            if (wasAtBottom) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.protocol_log_title)
                .setView(content)
                .setPositiveButton(R.string.log_clear, null)
                .setNegativeButton(R.string.log_close, null)
                .create();
        // Clear keeps the dialog open, so replace the default dismiss listener of the button
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> protocolLog.clear()));
        dialog.setOnDismissListener(dismissed -> {
            protocolLog.removeListener(listener);
            protocolLogDialog = null;
        });

        protocolLog.addListener(listener);
        protocolLogDialog = dialog;
        dialog.show();
        limitLogDialogHeight(scrollView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * Shows the persistent communication log file with Share and Clear. Opened from the About tab in every build,
     * so a user can send the file when the controller misbehaves.
     */
    public void showCommunicationLog() {
        if (communicationLogDialog != null && communicationLogDialog.isShowing()) {
            return;
        }
        ProtocolFileLog fileLog = bluetoothHelper.getProtocolFileLog();
        View content = getLayoutInflater().inflate(R.layout.dialog_protocol_log, null);
        TextView textView = content.findViewById(R.id.tv_protocol_log);
        ScrollView scrollView = content.findViewById(R.id.scroll_protocol_log);
        loadFileLogText(textView, scrollView, fileLog);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.communication_log_title)
                .setView(content)
                .setPositiveButton(R.string.communication_log_share, null)
                .setNeutralButton(R.string.log_clear, null)
                .setNegativeButton(R.string.log_close, null)
                .create();
        // Share and Clear keep the dialog open, so replace the default dismiss listeners of the buttons
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> shareCommunicationLog(fileLog));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> clearFileLog(textView, fileLog));
        });
        dialog.setOnDismissListener(dismissed -> communicationLogDialog = null);
        communicationLogDialog = dialog;
        dialog.show();
        limitLogDialogHeight(scrollView);
    }

    /**
     * Limits the log dialog to a share of the screen height, so it also fits in landscape and on small screens.
     * Must run after {@code dialog.show()}: the view inflated with a {@code null} root has no layout params until
     * the dialog attaches it.
     */
    private void limitLogDialogHeight(@NonNull ScrollView scrollView) {
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
        ViewGroup.LayoutParams params = scrollView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        } else {
            params.height = maxHeight;
        }
        scrollView.setLayoutParams(params);
    }

    private static boolean isScrolledToBottom(@NonNull ScrollView scrollView) {
        View child = scrollView.getChildAt(0);
        return child == null
                || scrollView.getScrollY() + scrollView.getHeight() >= child.getBottom();
    }

    /** Reads the log file in the background and shows it, scrolling to the newest entries. */
    private void loadFileLogText(@NonNull TextView textView, @NonNull ScrollView scrollView,
                                 @NonNull ProtocolFileLog fileLog) {
        logIoExecutor.execute(() -> {
            String text = fileLog.readAll();
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (text == null) {
                    textView.setText(R.string.communication_log_read_failed);
                } else {
                    textView.setText(text.isEmpty() ? getString(R.string.communication_log_empty) : text);
                }
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            });
        });
    }

    /** Empties the log file in the background and updates the dialog when it is done. */
    private void clearFileLog(@NonNull TextView textView, @NonNull ProtocolFileLog fileLog) {
        logIoExecutor.execute(() -> {
            boolean cleared = fileLog.clear();
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (!cleared) {
                    Toast.makeText(this, R.string.communication_log_clear_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                textView.setText(R.string.communication_log_empty);
            });
        });
    }

    private void shareCommunicationLog(@NonNull ProtocolFileLog fileLog) {
        File file = fileLog.getFile();
        if (!file.exists() || file.length() == 0) {
            Toast.makeText(this, R.string.communication_log_nothing_to_share, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.communication_log_share_title)));
    }

    private void renderProtocolLog(@NonNull TextView textView, @NonNull ProtocolLog protocolLog) {
        List<ProtocolLog.Entry> entries = protocolLog.getEntries();
        if (entries.isEmpty()) {
            textView.setText(R.string.protocol_log_empty);
            return;
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        StringBuilder builder = new StringBuilder();
        for (ProtocolLog.Entry entry : entries) {
            builder.append(timeFormat.format(new Date(entry.getTimestampMs())))
                    .append(entry.isSent() ? "  > " : "  < ")
                    .append(entry.getMessage())
                    .append('\n');
        }
        textView.setText(builder.toString());
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
        if (protocolLogDialog != null) {
            protocolLogDialog.dismiss();
            protocolLogDialog = null;
        }
        if (communicationLogDialog != null) {
            communicationLogDialog.dismiss();
            communicationLogDialog = null;
        }
        logIoExecutor.shutdown();
        super.onDestroy();
        // Close the connection only when the app is really finishing (not on rotation)
        if (isFinishing()) {
            ((DropControllerApp) getApplication()).getBluetoothHelper().disconnect();
        }
    }

    private static final int[] TAB_IDS = {
            R.id.nav_drops, R.id.nav_settings, R.id.nav_graph, R.id.nav_drain, R.id.nav_about
    };

    private static String tabTag(int navItemId) {
        return "tab_" + navItemId;
    }

    @Nullable
    private static Fragment createTab(int navItemId) {
        if (navItemId == R.id.nav_drops) {
            return new DropsFragment();
        } else if (navItemId == R.id.nav_settings) {
            return new SettingsFragment();
        } else if (navItemId == R.id.nav_graph) {
            return new GraphFragment();
        } else if (navItemId == R.id.nav_drain) {
            return new DrainFragment();
        } else if (navItemId == R.id.nav_about) {
            return new AboutFragment();
        }
        return null;
    }

    /**
     * The restored fragment manager keeps the hidden tabs, but not their lifecycle limit, so set it again.
     */
    private void limitHiddenTabs() {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction().setReorderingAllowed(true);
        for (int tabId : TAB_IDS) {
            Fragment tab = manager.findFragmentByTag(tabTag(tabId));
            if (tab != null && tab.isHidden()) {
                transaction.setMaxLifecycle(tab, Lifecycle.State.STARTED);
            }
        }
        transaction.commit();
    }

    /**
     * Shows a tab. A tab fragment is created on its first visit and then only hidden and shown, so switching back
     * does not inflate its views again. A hidden fragment is limited to STARTED: it gets onPause, but not onStop.
     */
    private boolean showTab(int navItemId) {
        FragmentManager manager = getSupportFragmentManager();
        Fragment target = manager.findFragmentByTag(tabTag(navItemId));
        if (target == null) {
            target = createTab(navItemId);
            if (target == null) {
                return false;
            }
        }

        // A field with focus is committed when it loses focus; the keyboard belongs to the tab that is left
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
            focused.clearFocus();
        }

        FragmentTransaction transaction = manager.beginTransaction().setReorderingAllowed(true);
        for (int tabId : TAB_IDS) {
            Fragment other = manager.findFragmentByTag(tabTag(tabId));
            if (other != null && other != target && !other.isHidden()) {
                transaction.hide(other).setMaxLifecycle(other, Lifecycle.State.STARTED);
            }
        }
        if (target.isAdded()) {
            transaction.show(target).setMaxLifecycle(target, Lifecycle.State.RESUMED);
        } else {
            transaction.add(R.id.fragment_container, target, tabTag(navItemId));
        }
        transaction.commit();
        return true;
    }

    // 4. Menu item click handler
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // R.id.nav_drops, R.id.nav_settings etc. are IDs from bottom_nav_menu.xml
        int itemId = item.getItemId();
        boolean loaded = showTab(itemId);
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