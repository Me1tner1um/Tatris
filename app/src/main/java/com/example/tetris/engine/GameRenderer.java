package com.example.tetris.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.example.tetris.graphics.SpriteManager;
import com.example.tetris.layout.ScreenLayout;
import com.example.tetris.layout.ScreenLayout.Box;
import com.example.tetris.logic.Board;
import com.example.tetris.logic.TetrisGame;
import com.example.tetris.logic.Tetromino;
import com.example.tetris.logic.TetrominoType;
import com.example.tetris.ui.ButtonPanel;
import com.example.tetris.ui.MenuLayout;
import com.example.tetris.ui.UiButton;

/**
 * Рисует игру в мастер-экран 256x480 (пиксель-арт, координаты из ScreenLayout) и выводит его на реальный экран.
 * Работа разделена на две фазы:
 *  1. renderFrame() читает состояние игры, поэтому вызывается под замком игры;
 *  2. drawTo() только копирует готовый кадр на экран и игру не трогает, замок не нужен.
 * Класс не потокобезопасен: все вызовы должны идти из одного потока (игрового).
 */
public class GameRenderer {

    private static final int CELL_SIZE = ScreenLayout.CELL_SIZE;
    private static final int BOARD_X = ScreenLayout.BOARD.left;
    private static final int BOARD_Y = ScreenLayout.BOARD.top;

    // Окна Hold и Next: фигура рисуется мини-клетками по центру рамки
    private static final int PREVIEW_CELL_SIZE = 8;

    // Строки статистики внизу: шаг между строками
    private static final int STATS_LINE_HEIGHT = 14;

    // Цвета заданы константами: Color.parseColor в каждом кадре создаёт лишний мусор для GC
    private static final int BACKGROUND_COLOR = 0xFF14141E;
    private static final int BOARD_COLOR = 0xFF08080C;
    private static final int PAUSE_DIM_COLOR = 0xB0000000;     // Затемнение игры под меню паузы
    private static final int BUTTON_FILL = 0xFF2A2E4A;
    private static final int BUTTON_FILL_PRESSED = 0xFF1A1C30;
    private static final int[] COLORS = {
            Color.TRANSPARENT,
            Color.CYAN,
            Color.BLUE,
            0xFFFFA500,
            Color.YELLOW,
            Color.GREEN,
            Color.MAGENTA,
            Color.RED
    };

    private final Bitmap virtualBitmap;
    private final Canvas virtualCanvas;
    private final Paint pixelPaint;
    private final Paint blockPaint;
    private final Paint textPaint;
    private final Paint debugPaint;
    private final Paint debugTextPaint;

    private final Rect srcRect = new Rect(0, 0, ScreenLayout.MASTER_WIDTH, ScreenLayout.MASTER_HEIGHT);
    private final RectF dstRect = new RectF();
    private final Rect previewSrcRect = new Rect(0, 0, CELL_SIZE, CELL_SIZE);
    private final Rect previewDstRect = new Rect();

    // Цвет, которым закрашивается всё, что мастер-экран не покрыл (например, на планшетах): цвет угла фона
    private int fillColor = BACKGROUND_COLOR;
    private boolean fillColorResolved = false;

    public GameRenderer() {
        virtualBitmap = Bitmap.createBitmap(ScreenLayout.MASTER_WIDTH, ScreenLayout.MASTER_HEIGHT, Bitmap.Config.ARGB_8888);
        virtualCanvas = new Canvas(virtualBitmap);

        pixelPaint = new Paint();
        pixelPaint.setFilterBitmap(false);
        pixelPaint.setDither(false);

        blockPaint = new Paint();
        blockPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(10);

        debugPaint = new Paint();
        debugPaint.setStyle(Paint.Style.STROKE);
        debugPaint.setStrokeWidth(2f);

        debugTextPaint = new Paint();
        debugTextPaint.setColor(Color.WHITE);
        debugTextPaint.setTextSize(28f);
    }

    // Фаза 2: выводит готовый кадр на экран. Игровое состояние не читает.
    public void drawTo(Canvas target, ScreenLayout layout, boolean debugOverlay) {
        target.drawColor(fillColor);
        if (layout == null) return;

        float scale = layout.scale();
        dstRect.set(layout.originX(), layout.originY(),
                layout.originX() + ScreenLayout.MASTER_WIDTH * scale,
                layout.originY() + ScreenLayout.MASTER_HEIGHT * scale);
        target.drawBitmap(virtualBitmap, srcRect, dstRect, pixelPaint);

        if (debugOverlay) {
            drawDebugOverlay(target, layout);
        }
    }

    // Отладка: рамки безопасной зоны и HUD прямо на экране, чтобы сверять рисунки с реальной раскладкой
    private void drawDebugOverlay(Canvas canvas, ScreenLayout layout) {
        drawDebugBox(canvas, layout, ScreenLayout.SAFE, Color.GREEN);
        drawDebugBox(canvas, layout, ScreenLayout.TOP_HUD, Color.rgb(120, 160, 255));
        drawDebugBox(canvas, layout, ScreenLayout.BOTTOM_HUD, Color.rgb(255, 190, 90));
        drawDebugBox(canvas, layout, ScreenLayout.BOARD, Color.WHITE);
        drawDebugBox(canvas, layout, ScreenLayout.MENU_BUTTON, Color.rgb(255, 190, 90));

        canvas.drawText("scale " + layout.scale(),
                layout.toScreenX(ScreenLayout.SAFE.left) + 8, layout.toScreenY(ScreenLayout.SAFE.top) + 34, debugTextPaint);
    }

    private void drawDebugBox(Canvas canvas, ScreenLayout layout, Box box, int color) {
        debugPaint.setColor(color);
        canvas.drawRect(layout.toScreenX(box.left), layout.toScreenY(box.top),
                layout.toScreenX(box.right), layout.toScreenY(box.bottom), debugPaint);
    }

    // Фаза 1: рисует один кадр в мастер-bitmap. Читает состояние игры, вызывать под замком игры.
    public void renderFrame(TetrisGame game) {
        // 1. Фон на весь мастер-экран (включая bleed)
        drawBackground();

        // 2. Фон внутри стакана
        blockPaint.setColor(BOARD_COLOR);
        virtualCanvas.drawRect(BOARD_X, BOARD_Y,
                BOARD_X + Board.WIDTH * CELL_SIZE,
                BOARD_Y + Board.HEIGHT * CELL_SIZE, blockPaint);

        // 3. Зафиксированные блоки в стакане
        int[][] grid = game.getBoard().getGrid();
        for (int r = 0; r < Board.HEIGHT; r++) {
            for (int c = 0; c < Board.WIDTH; c++) {
                if (grid[r][c] != 0) {
                    drawCell(virtualCanvas, BOARD_X, BOARD_Y, c, r, grid[r][c]);
                }
            }
        }

        Tetromino current = game.getCurrentPiece();
        if (current != null && !game.isGameOver()) {
            int[][] shape = current.getShape();
            int typeId = current.getType().getId();

            // 4. Ghost Piece (проекция)
            int ghostY = game.getGhostY();
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        drawGhostCell(virtualCanvas, BOARD_X, BOARD_Y, current.getX() + c, ghostY + r, typeId);
                    }
                }
            }

            // 5. Падающая фигура
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] != 0) {
                        drawCell(virtualCanvas, BOARD_X, BOARD_Y, current.getX() + c, current.getY() + r, typeId);
                    }
                }
            }
        }

        // 6. HUD: Hold и Next сверху, статистика и кнопка меню снизу
        drawHud(virtualCanvas, game);

        // 7. Контур стакана
        blockPaint.setStyle(Paint.Style.STROKE);
        blockPaint.setColor(Color.WHITE);
        virtualCanvas.drawRect(BOARD_X, BOARD_Y,
                BOARD_X + Board.WIDTH * CELL_SIZE,
                BOARD_Y + Board.HEIGHT * CELL_SIZE, blockPaint);
        blockPaint.setStyle(Paint.Style.FILL);

        if (game.isGameOver()) {
            float centerX = ScreenLayout.BOARD.centerX();
            float centerY = ScreenLayout.BOARD.centerY();

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.RED);
            textPaint.setTextSize(16);
            virtualCanvas.drawText("GAME OVER", centerX, centerY, textPaint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(10);
            virtualCanvas.drawText("Tap to restart", centerX, centerY + 20, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    // Фон на весь мастер-экран (включая bleed). Если картинки нет, запасной тёмный цвет.
    private void drawBackground() {
        Bitmap background = SpriteManager.getBackgroundSprite();
        if (background != null) {
            virtualCanvas.drawBitmap(background, 0, 0, null);
            if (!fillColorResolved) {
                fillColor = background.getPixel(0, 0);
                fillColorResolved = true;
            }
        } else {
            virtualCanvas.drawColor(BACKGROUND_COLOR);
        }
    }

    // Главное меню: фон, логотип и кнопки. Читает состояние кнопок, вызывать под замком игры.
    public void renderMainMenu(ButtonPanel panel) {
        drawBackground();
        drawLogo();
        drawButtons(panel);
    }

    // Меню паузы поверх уже нарисованного кадра игры: затемнение, заголовок и кнопки. Вызывать под замком игры.
    public void renderPauseOverlay(ButtonPanel panel) {
        blockPaint.setStyle(Paint.Style.FILL);
        blockPaint.setColor(PAUSE_DIM_COLOR);
        virtualCanvas.drawRect(0, 0, ScreenLayout.MASTER_WIDTH, ScreenLayout.MASTER_HEIGHT, blockPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20);
        virtualCanvas.drawText("PAUSED", MenuLayout.TITLE_CENTER_X, MenuLayout.PAUSE_TITLE_BASELINE, textPaint);
        textPaint.setTextSize(10);
        textPaint.setTextAlign(Paint.Align.LEFT);

        drawButtons(panel);
    }

    // Логотип из файла или, пока его нет, простая рамка с названием
    private void drawLogo() {
        Box box = MenuLayout.LOGO_BOX;
        Bitmap logo = SpriteManager.getLogoSprite();
        if (logo != null) {
            virtualCanvas.drawBitmap(logo, box.left, box.top, null);
            return;
        }

        blockPaint.setStyle(Paint.Style.STROKE);
        blockPaint.setColor(Color.GRAY);
        virtualCanvas.drawRect(box.left, box.top, box.right, box.bottom, blockPaint);
        blockPaint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);
        virtualCanvas.drawText("TETRIS", box.centerX(), box.centerY() + 10, textPaint);
        textPaint.setTextSize(10);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawButtons(ButtonPanel panel) {
        for (UiButton button : panel.buttons()) {
            Box box = button.box();
            boolean pressed = panel.isPressed(button);

            Bitmap normal = SpriteManager.getButtonSprite(button.id());
            Bitmap pressedSprite = pressed ? SpriteManager.getButtonPressedSprite(button.id()) : null;

            if (pressedSprite != null) {
                virtualCanvas.drawBitmap(pressedSprite, box.left, box.top, null);
            } else if (normal != null) {
                // Без отдельного спрайта нажатая кнопка просто сдвигается на пиксель
                int shift = pressed ? 1 : 0;
                virtualCanvas.drawBitmap(normal, box.left + shift, box.top + shift, null);
            } else {
                drawFallbackButton(button, pressed);
            }
        }
    }

    // Кнопка без картинок: рамка и подпись
    private void drawFallbackButton(UiButton button, boolean pressed) {
        Box box = button.box();

        blockPaint.setStyle(Paint.Style.FILL);
        blockPaint.setColor(pressed ? BUTTON_FILL_PRESSED : BUTTON_FILL);
        virtualCanvas.drawRect(box.left, box.top, box.right, box.bottom, blockPaint);

        blockPaint.setStyle(Paint.Style.STROKE);
        blockPaint.setColor(Color.WHITE);
        virtualCanvas.drawRect(box.left, box.top, box.right, box.bottom, blockPaint);
        blockPaint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(12);
        virtualCanvas.drawText(button.id().label(), box.centerX(), box.centerY() + 4, textPaint);
        textPaint.setTextSize(10);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawHud(Canvas canvas, TetrisGame game) {
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(10);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // --- Сверху: HOLD слева, NEXT справа ---
        drawPreviewFrame(canvas, ScreenLayout.HOLD_BOX);
        drawPreviewFrame(canvas, ScreenLayout.NEXT_BOX);

        canvas.drawText("HOLD", ScreenLayout.HOLD_BOX.right + 4, ScreenLayout.HOLD_BOX.top + 12, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("NEXT", ScreenLayout.NEXT_BOX.left - 4, ScreenLayout.NEXT_BOX.top + 12, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        if (game.getHoldPiece() != null) {
            drawPreviewPiece(canvas, game.getHoldPiece(), ScreenLayout.HOLD_BOX);
        }
        if (game.getNextPieceType() != null) {
            drawPreviewPiece(canvas, game.getNextPieceType(), ScreenLayout.NEXT_BOX);
        }

        // --- Снизу: статистика ---
        int x = ScreenLayout.BOARD.left;
        int y = ScreenLayout.BOTTOM_HUD.top + 14;
        canvas.drawText("SCORE: " + game.getScore(), x, y, textPaint);
        y += STATS_LINE_HEIGHT;
        canvas.drawText("LINES: " + game.getLinesClearedTotal(), x, y, textPaint);
        y += STATS_LINE_HEIGHT;
        canvas.drawText("LVL: " + game.getLevel(), x, y, textPaint);

        // --- Снизу: кнопка меню (пока только рамка, нажатие появится на следующем этапе) ---
        Box menu = ScreenLayout.MENU_BUTTON;
        blockPaint.setStyle(Paint.Style.STROKE);
        blockPaint.setColor(Color.GRAY);
        canvas.drawRect(menu.left, menu.top, menu.right, menu.bottom, blockPaint);
        blockPaint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("MENU", menu.centerX(), menu.centerY() + 4, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawPreviewFrame(Canvas canvas, Box box) {
        blockPaint.setStyle(Paint.Style.STROKE);
        blockPaint.setColor(Color.GRAY);
        canvas.drawRect(box.left, box.top, box.right, box.bottom, blockPaint);
        blockPaint.setStyle(Paint.Style.FILL);
    }

    private void drawCell(Canvas canvas, int offsetX, int offsetY, int gridX, int gridY, int typeId) {
        int px = offsetX + gridX * CELL_SIZE;
        int py = offsetY + gridY * CELL_SIZE;

        Bitmap sprite = SpriteManager.getBlockSprite(typeId);
        if (sprite != null) {
            canvas.drawBitmap(sprite, px, py, null);
        } else {
            // Резервная заливка квадратами
            blockPaint.setColor(COLORS[typeId]);
            canvas.drawRect(px + 1, py + 1, px + CELL_SIZE - 1, py + CELL_SIZE - 1, blockPaint);
        }
    }

    private void drawGhostCell(Canvas canvas, int offsetX, int offsetY, int gridX, int gridY, int typeId) {
        int px = offsetX + gridX * CELL_SIZE;
        int py = offsetY + gridY * CELL_SIZE;

        Bitmap ghostSprite = SpriteManager.getGhostSprite();
        if (ghostSprite != null) {
            canvas.drawBitmap(ghostSprite, px, py, null);
        } else {
            blockPaint.setStyle(Paint.Style.STROKE);
            blockPaint.setColor(COLORS[typeId]);
            canvas.drawRect(px + 2, py + 2, px + CELL_SIZE - 2, py + CELL_SIZE - 2, blockPaint);
            blockPaint.setStyle(Paint.Style.FILL);
        }
    }

    // Рисует фигуру мини-клетками по центру рамки box.
    // Матрицы фигур содержат пустые строки и столбцы (у "I" заполнена одна строка из четырёх),
    // поэтому центрируется реальный контур фигуры, а не вся матрица.
    private void drawPreviewPiece(Canvas canvas, TetrominoType type, Box box) {
        int[][] shape = type.getShape();
        int typeId = type.getId();

        int minRow = Integer.MAX_VALUE, maxRow = -1;
        int minCol = Integer.MAX_VALUE, maxCol = -1;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        if (maxRow < 0) return;

        int width = (maxCol - minCol + 1) * PREVIEW_CELL_SIZE;
        int height = (maxRow - minRow + 1) * PREVIEW_CELL_SIZE;
        int originX = box.left + (box.width() - width) / 2;
        int originY = box.top + (box.height() - height) / 2;

        Bitmap sprite = SpriteManager.getBlockSprite(typeId);

        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                if (shape[r][c] == 0) continue;

                int px = originX + (c - minCol) * PREVIEW_CELL_SIZE;
                int py = originY + (r - minRow) * PREVIEW_CELL_SIZE;

                if (sprite != null) {
                    // Масштабируем блок 16x16 в мини-блок 8x8
                    previewDstRect.set(px, py, px + PREVIEW_CELL_SIZE, py + PREVIEW_CELL_SIZE);
                    canvas.drawBitmap(sprite, previewSrcRect, previewDstRect, pixelPaint);
                } else {
                    blockPaint.setColor(COLORS[typeId]);
                    canvas.drawRect(px + 1, py + 1,
                            px + PREVIEW_CELL_SIZE - 1, py + PREVIEW_CELL_SIZE - 1, blockPaint);
                }
            }
        }
    }
}
