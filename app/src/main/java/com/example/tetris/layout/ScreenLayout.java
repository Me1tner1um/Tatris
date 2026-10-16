package com.example.tetris.layout;

import com.example.tetris.logic.Board;

/**
 * Раскладка экрана: безопасная зона + bleed.
 *
 * Весь кадр рисуется в "мастер-экран" 256x480 виртуальных пикселей. В его центре лежит безопасная зона 180x440:
 * в ней находится всё важное (поле, Hold, Next, статистика, кнопка меню) и она гарантированно видна на любом экране.
 * Остальное (bleed) - только декор фона: на узких и высоких экранах он обрезается, на широких виден целиком.
 *
 * Класс хранит два вида данных:
 *  1. Константы в координатах мастер-экрана: это "контракт" между кодом и рисунками. Меняя их, меняйте и арт.
 *  2. Экземпляр с результатом расчёта: масштаб и положение мастер-экрана на конкретном экране телефона.
 * Класс не зависит от Android, поэтому покрыт обычными юнит-тестами.
 */
public final class ScreenLayout {

    public static final int CELL_SIZE = 16;

    public static final int MASTER_WIDTH = 256;
    public static final int MASTER_HEIGHT = 480;

    /** Прямоугольник в координатах мастер-экрана. */
    public static final class Box {
        public final int left, top, right, bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() { return right - left; }
        public int height() { return bottom - top; }
        public float centerX() { return (left + right) / 2f; }
        public float centerY() { return (top + bottom) / 2f; }

        public boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        public boolean containsBox(Box other) {
            return other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom;
        }

        public Box inflate(int amount) {
            return new Box(left - amount, top - amount, right + amount, bottom + amount);
        }
    }

    // ---- Безопасная зона и HUD (мастер-координаты) ----
    public static final Box SAFE = new Box(38, 20, 218, 460);           // 180x440
    public static final Box TOP_HUD = new Box(38, 20, 218, 72);         // Hold и Next
    public static final Box BOARD = new Box(48, 76,
            48 + Board.WIDTH * CELL_SIZE, 76 + Board.HEIGHT * CELL_SIZE); // 160x320
    public static final Box BOTTOM_HUD = new Box(38, 400, 218, 460);    // статистика и кнопка меню
    public static final Box HOLD_BOX = new Box(48, 24, 88, 64);
    public static final Box NEXT_BOX = new Box(168, 24, 208, 64);
    public static final Box MENU_BUTTON = new Box(172, 424, 208, 456);

    // Запас вокруг кнопки меню для удобного нажатия пальцем
    private static final int MENU_TAP_SLOP = 4;

    /** Что находится под пальцем при тапе. */
    public enum TapZone {
        HOLD,   // Верхняя левая часть: тап = Hold
        MENU,   // Кнопка меню
        FIELD   // Всё остальное: тап = поворот
    }

    /** Определяет зону тапа по координатам мастер-экрана. */
    public static TapZone zoneAtMaster(float masterX, float masterY) {
        if (MENU_BUTTON.inflate(MENU_TAP_SLOP).contains(masterX, masterY)) {
            return TapZone.MENU;
        }
        // Верхняя левая часть до верха поля, включая область над безопасной зоной: так проще попасть пальцем
        if (masterY < BOARD.top && masterX < MASTER_WIDTH / 2f) {
            return TapZone.HOLD;
        }
        return TapZone.FIELD;
    }

    // ---- Результат расчёта для конкретного экрана ----
    private final float scale;
    private final float originX;
    private final float originY;

    private ScreenLayout(float scale, float originX, float originY) {
        this.scale = scale;
        this.originX = originX;
        this.originY = originY;
    }

    /**
     * Вписывает безопасную зону в доступную область экрана (весь экран минус вырезы) по центру,
     * сохраняя пропорции. Мастер-экран при этом оказывается вокруг неё.
     *
     * @param integerScale true: масштаб округляется вниз до целого (чёткие пиксели, но больше пустых полей)
     */
    public static ScreenLayout compute(int surfaceWidth, int surfaceHeight,
                                       int insetLeft, int insetTop, int insetRight, int insetBottom,
                                       boolean integerScale) {
        float availWidth = Math.max(1, surfaceWidth - insetLeft - insetRight);
        float availHeight = Math.max(1, surfaceHeight - insetTop - insetBottom);

        float scale = Math.min(availWidth / SAFE.width(), availHeight / SAFE.height());
        if (integerScale && scale >= 1f) {
            scale = (float) Math.floor(scale);
        }

        float safeLeftOnScreen = insetLeft + (availWidth - SAFE.width() * scale) / 2f;
        float safeTopOnScreen = insetTop + (availHeight - SAFE.height() * scale) / 2f;

        float originX = safeLeftOnScreen - SAFE.left * scale;
        float originY = safeTopOnScreen - SAFE.top * scale;
        if (integerScale) {
            // Целые координаты, чтобы пиксели рисунка совпадали с пикселями экрана
            originX = Math.round(originX);
            originY = Math.round(originY);
        }
        return new ScreenLayout(scale, originX, originY);
    }

    /** Сколько пикселей экрана приходится на один виртуальный пиксель. */
    public float scale() { return scale; }

    /** Положение левого верхнего угла мастер-экрана на экране телефона (может быть отрицательным: bleed обрезан). */
    public float originX() { return originX; }
    public float originY() { return originY; }

    public float toScreenX(float masterX) { return originX + masterX * scale; }
    public float toScreenY(float masterY) { return originY + masterY * scale; }
    public float toMasterX(float screenX) { return (screenX - originX) / scale; }
    public float toMasterY(float screenY) { return (screenY - originY) / scale; }

    /** Определяет зону тапа по координатам на экране (в пикселях View). */
    public TapZone zoneAt(float screenX, float screenY) {
        return zoneAtMaster(toMasterX(screenX), toMasterY(screenY));
    }
}
