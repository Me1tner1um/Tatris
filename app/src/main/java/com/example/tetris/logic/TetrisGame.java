package com.example.tetris.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class TetrisGame {

    // Минимальный размер очереди фигур: гарантирует, что окно NEXT всегда есть что показывать
    private static final int MIN_QUEUE_SIZE = 7;

    // Смещения Wall Kick: (0,0), (-1,0), (1,0), (-2,0), (2,0), (0,-1)
    private static final int[][] KICK_OFFSETS = {{0, 0}, {-1, 0}, {1, 0}, {-2, 0}, {2, 0}, {0, -1}};

    private final Board board;
    private final LinkedList<TetrominoType> bag;
    private final Random random;

    private Tetromino currentPiece;
    private TetrominoType holdPiece;
    private boolean canHold;

    private int score;
    private int linesClearedTotal;
    private int level;
    private boolean isGameOver;

    public TetrisGame() {
        this(new Random());
    }

    // Явный Random нужен тестам: с фиксированным seed порядок фигур воспроизводим
    public TetrisGame(Random random) {
        this.random = random;
        this.board = new Board();
        this.bag = new LinkedList<>();
        reset();
    }

    // Полный сброс состояния: используется и при создании игры, и при рестарте
    private void reset() {
        board.clear();
        bag.clear();

        currentPiece = null;
        holdPiece = null;
        canHold = true;

        score = 0;
        linesClearedTotal = 0;
        level = 1;
        isGameOver = false;

        fillQueue();
        spawnNextPiece();
    }

    public void restart() {
        reset();
    }

    // 7-Bag генератор: добавляем перемешанные "мешки" по 7 фигур, пока очередь не станет достаточной
    private void fillQueue() {
        while (bag.size() < MIN_QUEUE_SIZE) {
            List<TetrominoType> newBag = new ArrayList<>();
            Collections.addAll(newBag, TetrominoType.values());
            Collections.shuffle(newBag, random);
            bag.addAll(newBag);
        }
    }

    // Только читает очередь, ничего не меняет (безопасно вызывать из отрисовки)
    public TetrominoType getNextPieceType() {
        return bag.peek();
    }

    private void spawnNextPiece() {
        TetrominoType nextType = bag.poll();
        fillQueue();
        spawn(nextType);
        canHold = true;
    }

    // Создаёт фигуру наверху стакана и проверяет Game Over
    private void spawn(TetrominoType type) {
        currentPiece = new Tetromino(type);
        if (!board.isValidPosition(currentPiece, currentPiece.getX(), currentPiece.getY())) {
            isGameOver = true;
        }
    }

    // Механика Hold (свайп вверх)
    public void hold() {
        if (!canHold || currentPiece == null || isGameOver) return;

        TetrominoType previousHold = holdPiece;
        holdPiece = currentPiece.getType();

        if (previousHold == null) {
            spawnNextPiece();
        } else {
            spawn(previousHold);
        }
        canHold = false;
    }

    // Поворот с системой Wall Kicks (сдвиги при упоре в стену/блоки)
    public void rotate() {
        if (currentPiece == null || isGameOver) return;

        currentPiece.rotateClockwise();

        boolean success = false;
        for (int[] offset : KICK_OFFSETS) {
            int newX = currentPiece.getX() + offset[0];
            int newY = currentPiece.getY() + offset[1];

            // Не даём фигуре уйти выше стакана: клетки с y < 0 не рисуются и теряются при фиксации
            if (newY < 0) continue;

            if (board.isValidPosition(currentPiece, newX, newY)) {
                currentPiece.setX(newX);
                currentPiece.setY(newY);
                success = true;
                break;
            }
        }

        // Если ни один сдвиг не подошел — отменяем поворот
        if (!success) {
            currentPiece.rotateCounterClockwise();
        }
    }

    public void moveLeft() {
        if (currentPiece != null && !isGameOver && board.isValidPosition(currentPiece, currentPiece.getX() - 1, currentPiece.getY())) {
            currentPiece.setX(currentPiece.getX() - 1);
        }
    }

    public void moveRight() {
        if (currentPiece != null && !isGameOver && board.isValidPosition(currentPiece, currentPiece.getX() + 1, currentPiece.getY())) {
            currentPiece.setX(currentPiece.getX() + 1);
        }
    }

    public void softDrop() {
        if (currentPiece != null && !isGameOver) {
            if (board.isValidPosition(currentPiece, currentPiece.getX(), currentPiece.getY() + 1)) {
                currentPiece.setY(currentPiece.getY() + 1);
                score += 1; // 1 очко за soft drop
            } else {
                lockPiece();
            }
        }
    }

    public void hardDrop() {
        if (currentPiece == null || isGameOver) return;

        int droppedCells = 0;
        while (board.isValidPosition(currentPiece, currentPiece.getX(), currentPiece.getY() + 1)) {
            currentPiece.setY(currentPiece.getY() + 1);
            droppedCells++;
        }
        score += droppedCells * 2; // 2 очка за каждую клетку hard drop
        lockPiece();
    }

    public void tick() {
        if (currentPiece == null || isGameOver) return;
        if (board.isValidPosition(currentPiece, currentPiece.getX(), currentPiece.getY() + 1)) {
            currentPiece.setY(currentPiece.getY() + 1);
        } else {
            lockPiece();
        }
    }

    private void lockPiece() {
        boolean placedAll = board.placeTetromino(currentPiece);
        int cleared = board.clearFullLines();
        if (cleared > 0) {
            updateScore(cleared);
        }
        // Если хотя бы один блок зафиксировался выше стакана — Lock Out (Game Over)
        if (!placedAll) {
            isGameOver = true;
            return;
        }
        spawnNextPiece();
    }

    private void updateScore(int lines) {
        linesClearedTotal += lines;
        switch (lines) {
            case 1: score += 100 * level; break;
            case 2: score += 300 * level; break;
            case 3: score += 500 * level; break;
            case 4: score += 800 * level; break; // TETRIS!
        }
        level = (linesClearedTotal / 10) + 1;
    }

    // Расчет позиции Ghost Piece
    public int getGhostY() {
        if (currentPiece == null) return 0;
        int ghostY = currentPiece.getY();
        while (board.isValidPosition(currentPiece, currentPiece.getX(), ghostY + 1)) {
            ghostY++;
        }
        return ghostY;
    }

    // Геттеры
    public Board getBoard() { return board; }
    public Tetromino getCurrentPiece() { return currentPiece; }
    public TetrominoType getHoldPiece() { return holdPiece; }
    public int getScore() { return score; }
    public int getLevel() { return level; }
    public int getLinesClearedTotal() { return linesClearedTotal; }
    public boolean isGameOver() { return isGameOver; }
}