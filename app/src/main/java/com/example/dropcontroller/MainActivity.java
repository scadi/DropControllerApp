package com.example.dropcontroller;

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

// Мы добавили BottomNavigationView.OnNavigationItemSelectedListener
public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    private Button splashButton;
    private RelativeLayout mainContentContainer;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Инициализация элементов
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
}