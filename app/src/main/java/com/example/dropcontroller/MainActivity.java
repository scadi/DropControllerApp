package com.example.dropcontroller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.example.dropcontroller.fragments.AboutFragment;
import com.example.dropcontroller.fragments.DrainFragment;
import com.example.dropcontroller.fragments.DropsFragment;
import com.example.dropcontroller.fragments.GraphFragment;
import com.example.dropcontroller.fragments.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest; // Import the Manifest class
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// Мы добавили BottomNavigationView.OnNavigationItemSelectedListener
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private Button splashButton;
    private RelativeLayout mainContentContainer;
    private BottomNavigationView bottomNavigationView;
    private final int REQUEST_BLUETOOTH_PERMISSIONS = 100; // Constant request code

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialization of views
        splashButton = findViewById(R.id.btn_splash_screen);
        mainContentContainer = findViewById(R.id.main_content_container);
        bottomNavigationView = findViewById(R.id.bottom_navigation);


        // 2. Установка слушателя нажатий для кнопки заставки (Урок 1)
        splashButton.setOnClickListener(v -> {
            splashButton.setVisibility(View.GONE);
            mainContentContainer.setVisibility(View.VISIBLE);

            // Инициализация навигации после скрытия заставки
            bottomNavigationView.setOnNavigationItemSelectedListener(this);

            // Загрузка первого Фрагмента (DropsFragment) при старте
            loadFragment(new DropsFragment());
        });

        // Call the method to check permissions immediately after creating the Activity
        checkAndRequestBluetoothPermissions();

    }

    // Метод для загрузки Фрагмента
    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment) // Замена текущего Фрагмента в контейнере
                    .commit(); // Выполнение операции
            return true;
        }
        return false;
    }

    // 4. Обработчик нажатий на пункты меню
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Fragment fragment = null;

        // R.id.nav_drops, R.id.nav_settings и т.д. - это ID из bottom_nav_menu.xml
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

        return loadFragment(fragment);
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
                Toast.makeText(this, "Bluetooth permissions granted.", Toast.LENGTH_SHORT).show();
            } else {
                // User denied one or more permissions
                Toast.makeText(this, "Bluetooth functionality is limited without permissions.", Toast.LENGTH_LONG).show();
            }
        }
    }

}