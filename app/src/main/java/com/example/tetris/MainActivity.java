package com.example.tetris;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.example.tetris.engine.GameView;

public class MainActivity extends AppCompatActivity {
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Рисуем и под вырезом камеры: фон-bleed заполняет эту область, а GameView сам отодвигает от выреза HUD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(params);
        }

        // Инициализация игрового View
        gameView = new GameView(this);
        gameView.setExitHandler(this::finish);
        setContentView(gameView);

        // "Назад": в игре пауза, на паузе продолжение, в главном меню выход. Решает GameView
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                gameView.onBackPressed();
            }
        });

        // Полноэкранный режим без устаревших методов
        hideSystemUI();
    }

    private void hideSystemUI() {
        // Указываем, что контент заходит под системные панели
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Создаем контроллер системных панелей через AndroidX
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        // Скрываем строки состояния (Status Bar) и навигации (Navigation Bar)
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // Панели будут появляться на короткое время только при свайпе от края экрана
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        gameView.resume();
    }
}