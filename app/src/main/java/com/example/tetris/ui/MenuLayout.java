package com.example.tetris.ui;

import com.example.tetris.layout.ScreenLayout.Box;

import java.util.Arrays;

/**
 * Раскладка меню в координатах мастер-экрана (безопасная зона 180x440 по центру).
 * Это контракт с рисунками: размеры кнопок и логотипа должны совпадать с файлами в assets/sprites/ui/.
 */
public final class MenuLayout {

    public static final int BUTTON_WIDTH = 120;
    public static final int BUTTON_HEIGHT = 36;
    private static final int BUTTON_STEP = 50;   // Расстояние между верхними краями соседних кнопок

    public static final int LOGO_WIDTH = 160;
    public static final int LOGO_HEIGHT = 100;

    private static final int CENTER_X = 128;
    private static final int BUTTON_LEFT = CENTER_X - BUTTON_WIDTH / 2;

    // Главное меню: логотип сверху, три кнопки ниже
    public static final Box LOGO_BOX = new Box(CENTER_X - LOGO_WIDTH / 2, 90, CENTER_X + LOGO_WIDTH / 2, 90 + LOGO_HEIGHT);
    private static final int MAIN_MENU_FIRST_TOP = 250;

    // Меню паузы: заголовок и три кнопки
    private static final int PAUSE_MENU_FIRST_TOP = 240;
    public static final int PAUSE_TITLE_BASELINE = 205;
    public static final int TITLE_CENTER_X = CENTER_X;

    private MenuLayout() {
    }

    private static UiButton button(ButtonId id, int firstTop, int index) {
        int top = firstTop + index * BUTTON_STEP;
        return new UiButton(id, new Box(BUTTON_LEFT, top, BUTTON_LEFT + BUTTON_WIDTH, top + BUTTON_HEIGHT));
    }

    public static ButtonPanel createMainMenu() {
        return new ButtonPanel(Arrays.asList(
                button(ButtonId.PLAY, MAIN_MENU_FIRST_TOP, 0),
                button(ButtonId.SETTINGS, MAIN_MENU_FIRST_TOP, 1),
                button(ButtonId.EXIT, MAIN_MENU_FIRST_TOP, 2)));
    }

    public static ButtonPanel createPauseMenu() {
        return new ButtonPanel(Arrays.asList(
                button(ButtonId.RESUME, PAUSE_MENU_FIRST_TOP, 0),
                button(ButtonId.SETTINGS, PAUSE_MENU_FIRST_TOP, 1),
                button(ButtonId.MAIN_MENU, PAUSE_MENU_FIRST_TOP, 2)));
    }
}
