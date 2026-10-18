package com.example.tetris.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.Objects;

public class InputHandler implements View.OnTouchListener {

    public interface InputEventListener {
        void onMoveLeft();
        void onMoveRight();
        void onTap(float x, float y);  // Координаты в пикселях View: что именно нажато, решает слушатель
        void onHardDrop();
        void onHold();
        void onSoftDropStart();  // Палец удерживается на месте: ускоряем падение
        void onSoftDropEnd();    // Палец отпущен (или жест сменился): возвращаем обычную скорость
    }

    // Пороги чувствительности в dp, а не в пикселях: жесты ощущаются одинаково на любой плотности экрана.
    // Значения подобраны как эквивалент прежних 55 / 85 / 45 / 25 px для экрана ~420 dpi (density 2.75).
    private static final float MOVE_STEP_DP = 20f;           // Шаг сдвига вбок (одна клетка)
    private static final float HARD_DROP_DISTANCE_DP = 31f;  // Дистанция свайпа вниз для мгновенного сброса
    private static final float SWIPE_UP_THRESHOLD_DP = 16f;  // Дистанция свайпа вверх для Hold
    private static final float TAP_MAX_DISTANCE_DP = 9f;     // Максимальный сдвиг пальца для тапа
    private static final long TAP_MAX_TIME_MS = 250;         // Максимальная длительность тапа
    private static final long SOFT_DROP_DELAY_MS = 280;       // Задержка удержания для soft drop

    // Вертикальный свайп считается вертикальным, если вертикальный путь больше горизонтального в столько раз
    private static final float VERTICAL_DOMINANCE = 1.3f;

    private static final int NO_POINTER = -1;

    private final InputEventListener listener;

    private final float moveStepPx;
    private final float hardDropDistancePx;
    private final float swipeUpThresholdPx;
    private final float tapMaxDistancePx;

    // Жест отслеживается только по первому пальцу, остальные касания игнорируются
    private int activePointerId = NO_POINTER;
    private float startX, startY;
    private float lastX;

    private boolean isHorizontalMoved;
    // Жест уже дал hard drop или Hold: дальнейшее движение пальца до отпускания игнорируется
    private boolean isGestureHandled;
    private boolean isSoftDropping;

    // Касания приходят в UI-потоке, и таймер удержания тоже должен срабатывать там
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable softDropStarter = this::startSoftDrop;

    public InputHandler(Context context, InputEventListener listener) {
        this.listener = Objects.requireNonNull(listener);

        float density = context.getResources().getDisplayMetrics().density;
        moveStepPx = MOVE_STEP_DP * density;
        hardDropDistancePx = HARD_DROP_DISTANCE_DP * density;
        swipeUpThresholdPx = SWIPE_UP_THRESHOLD_DP * density;
        tapMaxDistancePx = TAP_MAX_DISTANCE_DP * density;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                endSoftDrop(); // Если предыдущий жест не завершился штатно
                activePointerId = event.getPointerId(0);
                startX = event.getX();
                startY = event.getY();
                lastX = startX;
                isHorizontalMoved = false;
                isGestureHandled = false;
                handler.postDelayed(softDropStarter, SOFT_DROP_DELAY_MS);
                return true;

            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(activePointerId);
                if (index >= 0 && !isGestureHandled) {
                    handleMove(event.getX(index), event.getY(index));
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // Если оторвали именно основной палец, завершаем жест; остальные пальцы игнорируем
                int index = event.getActionIndex();
                if (event.getPointerId(index) == activePointerId) {
                    handleUp(v, event.getX(index), event.getY(index),
                            event.getEventTime() - event.getDownTime());
                    endSoftDrop();
                    activePointerId = NO_POINTER;
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                int index = event.findPointerIndex(activePointerId);
                if (index >= 0) {
                    handleUp(v, event.getX(index), event.getY(index),
                            event.getEventTime() - event.getDownTime());
                }
                endSoftDrop();
                activePointerId = NO_POINTER;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                // Система отобрала жест (например, свайп от края экрана): ничего не срабатывает
                endSoftDrop();
                activePointerId = NO_POINTER;
                return true;
        }
        return false;
    }

    /** Прерывает текущий жест: нужно, когда экран сменился (пауза, меню) и оставшиеся события жеста игре уже не нужны. */
    public void cancelGesture() {
        endSoftDrop();
        activePointerId = NO_POINTER;
    }

    private void startSoftDrop() {
        if (activePointerId == NO_POINTER || isHorizontalMoved || isGestureHandled) {
            return;
        }
        isSoftDropping = true;
        listener.onSoftDropStart();
    }

    // Идемпотентно: отменяет ожидающий запуск и, если ускорение шло, сообщает об окончании
    private void endSoftDrop() {
        handler.removeCallbacks(softDropStarter);
        if (isSoftDropping) {
            isSoftDropping = false;
            listener.onSoftDropEnd();
        }
    }

    private void handleMove(float x, float y) {
        float totalDeltaX = x - startX;
        float totalDeltaY = y - startY;

        // 0. Палец сдвинулся дальше допуска тапа: это уже не "удержание на месте", ускорение не включаем
        if (!isSoftDropping
                && (Math.abs(totalDeltaX) >= tapMaxDistancePx || Math.abs(totalDeltaY) >= tapMaxDistancePx)) {
            handler.removeCallbacks(softDropStarter);
        }

        // 1. Мгновенный hard drop (свайп вниз). Проверяем первым, чтобы быстрый свайп срабатывал сразу
        if (totalDeltaY >= hardDropDistancePx
                && totalDeltaY > Math.abs(totalDeltaX) * VERTICAL_DOMINANCE) {
            endSoftDrop();
            listener.onHardDrop();
            isGestureHandled = true;
            return;
        }

        // 2. Мгновенный Hold (свайп вверх) с любого места экрана. Срабатывает сразу, как только набрана дистанция:
        // дуга большого пальца и пауза перед свайпом ему не мешают (после паузы ускорение просто выключается).
        if (-totalDeltaY >= swipeUpThresholdPx
                && -totalDeltaY > Math.abs(totalDeltaX) * VERTICAL_DOMINANCE) {
            endSoftDrop();
            listener.onHold();
            isGestureHandled = true;
            return;
        }

        // 3. Движение влево / вправо: одна клетка на каждый пройденный шаг.
        // Цикл нужен, чтобы быстрый свайп не "терял" клетки, а сдвигал фигуру на все пройденные шаги.
        while (Math.abs(x - lastX) >= moveStepPx) {
            if (x > lastX) {
                listener.onMoveRight();
                lastX += moveStepPx;
            } else {
                listener.onMoveLeft();
                lastX -= moveStepPx;
            }
            isHorizontalMoved = true;
        }
    }

    private void handleUp(View v, float x, float y, long duration) {
        // Последняя точка жеста тоже может завершить свайп или сдвиг (если система не прислала промежуточных MOVE)
        if (!isGestureHandled) {
            handleMove(x, y);
        }
        if (isGestureHandled) {
            return;
        }

        // Жест был ускорением: отпускание пальца его просто завершает, тап не срабатывает
        if (isSoftDropping) {
            endSoftDrop();
            return;
        }
        handler.removeCallbacks(softDropStarter);

        float finalDeltaX = x - startX;
        float finalDeltaY = y - startY;

        // Тап
        if (!isHorizontalMoved
                && Math.abs(finalDeltaX) < tapMaxDistancePx
                && Math.abs(finalDeltaY) < tapMaxDistancePx
                && duration < TAP_MAX_TIME_MS) {

            // performClick нужен для accessibility, вызываем его только на настоящем тапе
            v.performClick();

            // Что под пальцем (поворот, Hold, меню), зависит от раскладки экрана: решает слушатель
            listener.onTap(startX, startY);
        }
    }
}
