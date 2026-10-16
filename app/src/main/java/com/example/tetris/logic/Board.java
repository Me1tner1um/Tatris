package com.example.tetris.logic;

public class Board {
    public static final int WIDTH = 10;
    public static final int HEIGHT = 20;

    // 0 = пусто, 1-7 = ID цвета фигуры
    private final int[][] grid;

    public Board() {
        grid = new int[HEIGHT][WIDTH];
    }

    // Проверка: помещается ли фигура в координаты (offsetX, offsetY)
    public boolean isValidPosition(Tetromino piece, int offsetX, int offsetY) {
        int[][] shape = piece.getShape();
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int newX = offsetX + c;
                    int newY = offsetY + r;

                    // Выход за левую/правую/нижнюю границу
                    if (newX < 0 || newX >= WIDTH || newY >= HEIGHT) {
                        return false;
                    }
                    // Выход за верхнюю границу игнорируем только если newY < 0
                    if (newY >= 0 && grid[newY][newX] != 0) {
                        return false; // Наложение на зафиксированный блок
                    }
                }
            }
        }
        return true;
    }

    // Запекание фигуры в матрицу стакана
    public void placeTetromino(Tetromino piece) {
        int[][] shape = piece.getShape();
        int px = piece.getX();
        int py = piece.getY();

        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    int boardY = py + r;
                    int boardX = px + c;
                    if (boardY >= 0 && boardY < HEIGHT && boardX >= 0 && boardX < WIDTH) {
                        grid[boardY][boardX] = piece.getType().getId();
                    }
                }
            }
        }
    }

    // Проверка и удаление заполненных рядов (возвращает количество сгоревших линий)
    public int clearFullLines() {
        int linesCleared = 0;
        for (int r = HEIGHT - 1; r >= 0; r--) {
            if (isLineFull(r)) {
                linesCleared++;
                removeLine(r);
                r++; // Перепроверяем эту же строку после сдвига сверху
            }
        }
        return linesCleared;
    }

    private boolean isLineFull(int row) {
        for (int c = 0; c < WIDTH; c++) {
            if (grid[row][c] == 0) return false;
        }
        return true;
    }

    private void removeLine(int row) {
        for (int r = row; r > 0; r--) {
            System.arraycopy(grid[r - 1], 0, grid[r], 0, WIDTH);
        }
        // Верхнюю строку заполняем нулями
        for (int c = 0; c < WIDTH; c++) {
            grid[0][c] = 0;
        }
    }

    public int[][] getGrid() { return grid; }

    public void clear() {
        for (int r = 0; r < HEIGHT; r++) {
            for (int c = 0; c < WIDTH; c++) {
                grid[r][c] = 0;
            }
        }
    }
}