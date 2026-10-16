package com.example.tetris.ui;

import com.example.tetris.layout.ScreenLayout.Box;

/** Кнопка меню: что это за кнопка и где она лежит (координаты мастер-экрана). */
public final class UiButton {
    private final ButtonId id;
    private final Box box;

    public UiButton(ButtonId id, Box box) {
        this.id = id;
        this.box = box;
    }

    public ButtonId id() { return id; }

    public Box box() { return box; }
}
