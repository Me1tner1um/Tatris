package com.example.tetris.logic;

public class Tetromino {
    private final TetrominoType type;
    private int[][] shape;
    private int x;
    private int y;

    public Tetromino(TetrominoType type) {
        this.type = type;
        this.shape = deepCopyMatrix(type.getShape());
        // Спавн по центру стакана (ширина стакана 10)
        this.x = (Board.WIDTH - shape[0].length) / 2;
        this.y = 0;
    }

    // Поворот матрицы по часовой стрелке
    public void rotateClockwise() {
        int rows = shape.length;
        int cols = shape[0].length;
        int[][] rotated = new int[cols][rows];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotated[c][rows - 1 - r] = shape[r][c];
            }
        }
        shape = rotated;
    }

    // Поворот против часовой стрелки (для отката при столкновении)
    public void rotateCounterClockwise() {
        int rows = shape.length;
        int cols = shape[0].length;
        int[][] rotated = new int[cols][rows];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rotated[cols - 1 - c][r] = shape[r][c];
            }
        }
        shape = rotated;
    }

    private int[][] deepCopyMatrix(int[][] original) {
        int[][] copy = new int[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        return copy;
    }

    public int[][] getShape() { return shape; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public TetrominoType getType() { return type; }
}