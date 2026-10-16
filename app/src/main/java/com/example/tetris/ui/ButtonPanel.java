package com.example.tetris.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Набор кнопок меню и логика нажатия (координаты мастер-экрана).
 * Кнопка срабатывает, если палец опустился на неё и отпущен тоже над ней, сколько бы ни длилось нажатие.
 * Если палец ушёл с кнопки и вернулся, она снова считается нажатой.
 */
public final class ButtonPanel {

    // Запас вокруг кнопки для удобного нажатия пальцем (в виртуальных пикселях)
    private static final int TOUCH_SLOP = 4;

    private final List<UiButton> buttons;

    private UiButton target;   // Кнопка, на которой началось касание
    private boolean inside;    // Палец сейчас над ней

    public ButtonPanel(List<UiButton> buttons) {
        this.buttons = Collections.unmodifiableList(new ArrayList<>(buttons));
    }

    public List<UiButton> buttons() {
        return buttons;
    }

    public void onDown(float x, float y) {
        target = hit(x, y);
        inside = target != null;
    }

    public void onMove(float x, float y) {
        if (target != null) {
            inside = contains(target, x, y);
        }
    }

    /** Возвращает нажатую кнопку или null, если палец отпущен не над той кнопкой, на которой был опущен. */
    public ButtonId onUp(float x, float y) {
        UiButton pressed = target;
        boolean clicked = pressed != null && contains(pressed, x, y);
        cancel();
        return clicked ? pressed.id() : null;
    }

    public void cancel() {
        target = null;
        inside = false;
    }

    /** Нажата ли кнопка прямо сейчас (для отрисовки). */
    public boolean isPressed(UiButton button) {
        return button == target && inside;
    }

    private UiButton hit(float x, float y) {
        for (UiButton button : buttons) {
            if (contains(button, x, y)) {
                return button;
            }
        }
        return null;
    }

    private static boolean contains(UiButton button, float x, float y) {
        return button.box().inflate(TOUCH_SLOP).contains(x, y);
    }
}
