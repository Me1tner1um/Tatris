package com.example.tetris.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import androidx.annotation.NonNull;

import com.example.tetris.graphics.SpriteManager;
import com.example.tetris.layout.ScreenLayout;
import com.example.tetris.logic.Gravity;
import com.example.tetris.logic.TetrisGame;
import com.example.tetris.logic.Tetromino;
import com.example.tetris.ui.ButtonId;
import com.example.tetris.ui.ButtonPanel;
import com.example.tetris.ui.MenuLayout;
import com.example.tetris.ui.ScreenFlow;

import java.util.function.Function;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, InputHandler.InputEventListener {

    // Пауза после Game Over, чтобы жест, начатый во время игры, не перезапустил её мгновенно
    private static final long RESTART_DELAY_MS = 600;
    private static final long NOT_SET = -1;

    // true: целый масштаб (чёткие пиксели, но поля шире). false: масштаб подбирается точно под экран
    private static final boolean INTEGER_SCALE = false;
    // true: поверх игры рисуются рамки безопасной зоны и HUD, чтобы сверять с ними рисунки
    private static final boolean DEBUG_LAYOUT = false;

    private final GameRenderer renderer = new GameRenderer();
    private final TetrisGame game = new TetrisGame();
    private final InputHandler inputHandler;

    // Какой экран сейчас (меню, игра, пауза) и кнопки меню. Меняются и читаются только под gameLock.
    private final ScreenFlow flow = new ScreenFlow();
    private final ButtonPanel mainMenuPanel = MenuLayout.createMainMenu();
    private final ButtonPanel pausePanel = MenuLayout.createPauseMenu();

    // Что делать по кнопке Exit (задаёт Activity)
    private Runnable exitHandler;

    // Замок игрового состояния: касания приходят в UI-потоке, а update() и отрисовка идут в GameThread
    private final Object gameLock = new Object();

    // volatile: пересчитывается в UI-потоке (размер экрана, вырезы), читается в игровом потоке
    private volatile ScreenLayout layout;

    // Размер Surface и вырезы камеры в пикселях. Меняются и читаются только в UI-потоке.
    private int surfaceWidth, surfaceHeight;
    private int insetLeft, insetTop, insetRight, insetBottom;

    private GameThread gameThread;
    private boolean surfaceReady;

    // Момент последнего шага гравитации (SystemClock.uptimeMillis: монотонные часы, не зависят от перевода времени)
    private long lastDropTime = SystemClock.uptimeMillis();
    private long gameOverAt = NOT_SET;

    // Ускорение падения (soft drop): включено, пока палец удерживается на месте. Меняется под gameLock.
    private boolean softDropActive;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        SpriteManager.init(context);

        inputHandler = new InputHandler(context, this);
        setOnTouchListener(this::dispatchTouch);
        setFocusable(true);
        setClickable(true);
    }

    public void update() {
        synchronized (gameLock) {
            if (isPlaying() && !game.isGameOver()) {
                long currentTime = SystemClock.uptimeMillis();
                if (currentTime - lastDropTime >= Gravity.dropIntervalMs(game.getLevel(), softDropActive)) {
                    lastDropTime = currentTime;
                    stepDown();
                }
            }
            syncGameOverState();
        }
    }

    // Один шаг гравитации. Вызывать под gameLock.
    private void stepDown() {
        if (!softDropActive) {
            game.tick();
            return;
        }

        // При ускорении каждая клетка даёт очко. Если фигура зафиксировалась, ускорение выключается,
        // иначе следующая фигура сама "улетела" бы вниз, пока палец ещё на экране.
        Tetromino before = game.getCurrentPiece();
        game.softDrop();
        if (game.getCurrentPiece() != before) {
            softDropActive = false;
        }
    }

    // Рисует один кадр на canvas, полученном из lockCanvas(). Вызывается из игрового потока.
    public void render(Canvas canvas) {
        if (canvas == null) return;

        // Состояние игры читается только под замком: касания не могут изменить его посреди отрисовки
        synchronized (gameLock) {
            switch (flow.screen()) {
                case MAIN_MENU:
                    renderer.renderMainMenu(mainMenuPanel);
                    break;
                case PAUSED:
                    renderer.renderFrame(game);
                    renderer.renderPauseOverlay(pausePanel);
                    break;
                default:
                    renderer.renderFrame(game);
            }
        }
        // Копирование готового кадра на экран игру не читает, замок не нужен
        renderer.drawTo(canvas, layout, DEBUG_LAYOUT);
    }

    // --- Экраны и меню ---

    public void setExitHandler(Runnable exitHandler) {
        this.exitHandler = exitHandler;
    }

    // Системная кнопка или жест "назад": в игре ставит на паузу, на паузе продолжает, в главном меню закрывает приложение
    public void onBackPressed() {
        navigate(ScreenFlow::onBack);
    }

    // Вызывать под gameLock
    private boolean isPlaying() {
        return flow.screen() == ScreenFlow.Screen.PLAYING;
    }

    // Выполняет переход между экранами. Вызывается только из UI-потока (касания, "назад", сворачивание).
    private void navigate(Function<ScreenFlow, ScreenFlow.Effect> step) {
        ScreenFlow.Effect effect;
        synchronized (gameLock) {
            effect = step.apply(flow);
            applyEffect(effect);
        }
        if (effect == ScreenFlow.Effect.EXIT_APP && exitHandler != null) {
            exitHandler.run();
        }
    }

    // Вызывать под gameLock
    private void applyEffect(ScreenFlow.Effect effect) {
        if (effect == ScreenFlow.Effect.NONE || effect == ScreenFlow.Effect.EXIT_APP) {
            return;
        }

        if (effect == ScreenFlow.Effect.START_NEW_GAME) {
            game.restart();
            gameOverAt = NOT_SET;
        }

        // Оставшиеся события прерванного жеста игре не нужны, а пауза не должна считаться временем падения
        inputHandler.cancelGesture();
        softDropActive = false;
        lastDropTime = SystemClock.uptimeMillis();
        mainMenuPanel.cancel();
        pausePanel.cancel();
    }

    // Все касания идут сюда: на экране игры их разбирает InputHandler (жесты), в меню - кнопки
    private boolean dispatchTouch(View v, MotionEvent event) {
        boolean playing;
        synchronized (gameLock) {
            playing = isPlaying();
        }
        if (playing) {
            return inputHandler.onTouch(v, event);
        }
        return handleMenuTouch(v, event);
    }

    private boolean handleMenuTouch(View v, MotionEvent event) {
        ScreenLayout current = layout;
        if (current == null) return true;

        float x = current.toMasterX(event.getX());
        float y = current.toMasterY(event.getY());

        ButtonId clicked = null;
        synchronized (gameLock) {
            ButtonPanel panel = flow.screen() == ScreenFlow.Screen.PAUSED ? pausePanel : mainMenuPanel;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    panel.onDown(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    panel.onMove(x, y);
                    break;
                case MotionEvent.ACTION_UP:
                    clicked = panel.onUp(x, y);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    panel.cancel();
                    break;
                default:
                    break;
            }
        }

        if (clicked != null) {
            performClick();
            final ButtonId id = clicked;
            navigate(f -> f.onButton(id));
        }
        return true;
    }

    // --- Input Handling ---

    // Запоминает момент Game Over (нужно для паузы перед рестартом). Вызывать под gameLock.
    private void syncGameOverState() {
        if (game.isGameOver()) {
            softDropActive = false;
            if (gameOverAt == NOT_SET) {
                gameOverAt = SystemClock.uptimeMillis();
            }
        } else {
            gameOverAt = NOT_SET;
        }
    }

    // Если игра окончена: перезапускает её (после паузы) и сообщает, что касание обработано. Вызывать под gameLock.
    private boolean handleGameOverTap() {
        if (!game.isGameOver()) {
            return false;
        }
        if (SystemClock.uptimeMillis() - gameOverAt >= RESTART_DELAY_MS) {
            game.restart();
            lastDropTime = SystemClock.uptimeMillis();
            gameOverAt = NOT_SET;
            softDropActive = false;
        }
        return true;
    }

    // Все обработчики вызываются в UI-потоке, поэтому меняют состояние игры только под gameLock.
    // После Game Over сдвиги и hard drop игнорируются (логика сама это проверяет), рестарт - только тапом.
    @Override
    public void onMoveLeft() {
        synchronized (gameLock) {
            if (isPlaying()) game.moveLeft();
        }
    }

    @Override
    public void onMoveRight() {
        synchronized (gameLock) {
            if (isPlaying()) game.moveRight();
        }
    }

    @Override
    public void onTap(float x, float y) {
        ScreenLayout current = layout;
        ScreenLayout.TapZone zone = current != null ? current.zoneAt(x, y) : ScreenLayout.TapZone.FIELD;

        switch (zone) {
            case HOLD:
                onHold();
                break;
            case MENU:
                navigate(ScreenFlow::onMenuButtonTap);
                break;
            default:
                rotate();
        }
    }

    private void rotate() {
        synchronized (gameLock) {
            if (!isPlaying()) return;
            if (!handleGameOverTap()) {
                game.rotate();
            }
            syncGameOverState();
        }
    }

    @Override
    public void onHardDrop() {
        synchronized (gameLock) {
            if (!isPlaying()) return;
            game.hardDrop();
            // Новая фигура не должна сразу же "провалиться" на шаг из-за старого таймера гравитации
            lastDropTime = SystemClock.uptimeMillis();
            softDropActive = false;
            syncGameOverState();
        }
    }

    @Override
    public void onSoftDropStart() {
        synchronized (gameLock) {
            softDropActive = isPlaying() && !game.isGameOver();
        }
    }

    @Override
    public void onSoftDropEnd() {
        synchronized (gameLock) {
            softDropActive = false;
        }
    }

    @Override
    public void onHold() {
        synchronized (gameLock) {
            if (!isPlaying()) return;
            if (!handleGameOverTap()) {
                game.hold();
                lastDropTime = SystemClock.uptimeMillis();
                softDropActive = false;
            }
            syncGameOverState();
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        surfaceReady = true;
        resume();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        updateLayout();
    }

    // Вырез камеры отодвигает безопасную зону: Hold и Next не должны оказаться под камерой
    @NonNull
    @Override
    public WindowInsets onApplyWindowInsets(@NonNull WindowInsets insets) {
        int left = 0, top = 0, right = 0, bottom = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                left = cutout.getSafeInsetLeft();
                top = cutout.getSafeInsetTop();
                right = cutout.getSafeInsetRight();
                bottom = cutout.getSafeInsetBottom();
            }
        }
        insetLeft = left;
        insetTop = top;
        insetRight = right;
        insetBottom = bottom;
        updateLayout();
        return super.onApplyWindowInsets(insets);
    }

    private void updateLayout() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return;
        layout = ScreenLayout.compute(surfaceWidth, surfaceHeight,
                insetLeft, insetTop, insetRight, insetBottom, INTEGER_SCALE);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        surfaceReady = false;
        pause();
    }

    // resume() и pause() вызываются только из UI-потока (Activity и колбэки Surface), поэтому gameThread не требует синхронизации
    public void resume() {
        // Без Surface рисовать некуда: поток запустится позже, из surfaceCreated()
        if (!surfaceReady) return;

        if (gameThread == null || !gameThread.isAlive()) {
            // Время паузы не должно считаться временем падения фигуры: иначе после возврата она резко "прыгает"
            synchronized (gameLock) {
                lastDropTime = SystemClock.uptimeMillis();
                softDropActive = false;
            }
            gameThread = new GameThread(getHolder(), this);
            gameThread.setRunning(true);
            gameThread.start();
        }
    }

    public void pause() {
        // Свернули приложение посреди партии: после возврата игрок увидит меню паузы
        navigate(ScreenFlow::onAppPaused);

        if (gameThread == null) return;

        gameThread.setRunning(false);
        boolean interrupted = false;
        while (true) {
            try {
                gameThread.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            // Возвращаем флаг прерывания вызывающему коду
            Thread.currentThread().interrupt();
        }
        gameThread = null;
    }
}
