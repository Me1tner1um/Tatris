package com.example.tetris.logic;

/** Скорость гравитации: интервал между шагами фигуры вниз. */
public final class Gravity {

    private static final long BASE_INTERVAL_MS = 600;   // Уровень 1
    private static final long LEVEL_STEP_MS = 50;       // Каждый уровень быстрее на 50 мс
    private static final long MIN_INTERVAL_MS = 100;    // Быстрее обычное падение не становится

    // Интервал шага при ускорении (soft drop): не медленнее обычного падения
    public static final long SOFT_DROP_INTERVAL_MS = 50;

    private Gravity() {
    }

    public static long dropIntervalMs(int level, boolean softDrop) {
        long normal = Math.max(MIN_INTERVAL_MS, BASE_INTERVAL_MS - (Math.max(1, level) - 1) * LEVEL_STEP_MS);
        return softDrop ? Math.min(normal, SOFT_DROP_INTERVAL_MS) : normal;
    }
}