package com.example.tetris.ui;

/**
 * Переходы между экранами приложения: главное меню, игра, пауза.
 * Чистая логика без Android: класс только решает, какой экран дальше, и говорит, что должен сделать вызывающий код (Effect).
 * Не потокобезопасен: вызывать под одним замком.
 */
public final class ScreenFlow {

    public enum Screen {
        MAIN_MENU,
        PLAYING,
        PAUSED
    }

    /** Что нужно сделать после перехода. */
    public enum Effect {
        NONE,
        START_NEW_GAME,   // Начать партию с нуля
        RESUME_GAME,      // Продолжить партию после паузы
        PAUSE_GAME,       // Партия остановлена
        SHOW_MAIN_MENU,   // Выход из партии в главное меню
        EXIT_APP          // Закрыть приложение
    }

    private Screen screen = Screen.MAIN_MENU;

    public Screen screen() {
        return screen;
    }

    /** Нажата кнопка меню. Кнопки, которых нет на текущем экране, и пустая кнопка настроек ничего не делают. */
    public Effect onButton(ButtonId id) {
        switch (screen) {
            case MAIN_MENU:
                if (id == ButtonId.PLAY) {
                    screen = Screen.PLAYING;
                    return Effect.START_NEW_GAME;
                }
                if (id == ButtonId.EXIT) {
                    return Effect.EXIT_APP;
                }
                return Effect.NONE;

            case PAUSED:
                if (id == ButtonId.RESUME) {
                    screen = Screen.PLAYING;
                    return Effect.RESUME_GAME;
                }
                if (id == ButtonId.MAIN_MENU) {
                    screen = Screen.MAIN_MENU;
                    return Effect.SHOW_MAIN_MENU;
                }
                return Effect.NONE;

            default:
                return Effect.NONE;
        }
    }

    /** Нажата кнопка меню в углу игрового экрана. */
    public Effect onMenuButtonTap() {
        return pauseIfPlaying();
    }

    /** Приложение свернули или закрыли экран: игру ставим на паузу. */
    public Effect onAppPaused() {
        return pauseIfPlaying();
    }

    /** Системная кнопка или жест "назад". */
    public Effect onBack() {
        switch (screen) {
            case PLAYING:
                return pauseIfPlaying();
            case PAUSED:
                screen = Screen.PLAYING;
                return Effect.RESUME_GAME;
            default:
                return Effect.EXIT_APP;
        }
    }

    private Effect pauseIfPlaying() {
        if (screen != Screen.PLAYING) {
            return Effect.NONE;
        }
        screen = Screen.PAUSED;
        return Effect.PAUSE_GAME;
    }
}
