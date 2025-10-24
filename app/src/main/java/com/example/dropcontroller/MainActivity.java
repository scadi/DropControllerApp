package com.example.dropcontroller;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    // 1. Объявляем переменные для элементов UI
    private Button splashButton;
    private LinearLayout mainContentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Устанавливаем XML-разметку как основной вид
        setContentView(R.layout.activity_main);

        // 2. Инициализация: "Привязка" Java-переменных к UI-элементам по ID
        splashButton = findViewById(R.id.btn_splash_screen);
        mainContentContainer = findViewById(R.id.main_content_container);

        // 3. Установка слушателя событий (Click Listener)
        splashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Логика App Inventor:
                // 1. Скрыть кнопку-заставку
                splashButton.setVisibility(View.GONE);

                // 2. Показать основной контейнер (TABS_VA)
                mainContentContainer.setVisibility(View.VISIBLE);

                // 3. Здесь в будущем будет логика инициализации (Bluetooth, загрузка настроек)
                // Но пока мы просто переключаем экраны.
            }
        });
    }
}