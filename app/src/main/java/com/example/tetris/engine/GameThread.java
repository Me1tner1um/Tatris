package com.example.tetris.engine;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private static final int TARGET_FPS = 60;
    private static final long FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS;

    private final SurfaceHolder surfaceHolder;
    private final GameView gameView;

    // volatile: флаг меняется из UI-потока, а читается игровым потоком
    private volatile boolean running;

    public GameThread(SurfaceHolder surfaceHolder, GameView gameView) {
        this.surfaceHolder = surfaceHolder;
        this.gameView = gameView;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void run() {
        while (running) {
            long frameStart = System.nanoTime();

            // Логика обновляется независимо от того, удалось ли получить canvas
            gameView.update();

            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    gameView.render(canvas);
                }
            } catch (Exception e) {
                // Surface might have been destroyed or become invalid
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        // Surface was released before unlock
                    }
                }
            }

            long sleepMs = (FRAME_TIME_NS - (System.nanoTime() - frameStart)) / 1_000_000L;
            if (sleepMs > 0) {
                try {
                    sleep(sleepMs);
                } catch (InterruptedException e) {
                    // Нас просят завершиться
                    return;
                }
            }
        }
    }
}