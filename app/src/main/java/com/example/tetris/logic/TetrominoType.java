package com.example.tetris.logic;

public enum TetrominoType {
    I(1, new int[][]{
            {0, 0, 0, 0},
            {1, 1, 1, 1},
            {0, 0, 0, 0},
            {0, 0, 0, 0}
    }),
    J(2, new int[][]{
            {1, 0, 0},
            {1, 1, 1},
            {0, 0, 0}
    }),
    L(3, new int[][]{
            {0, 0, 1},
            {1, 1, 1},
            {0, 0, 0}
    }),
    O(4, new int[][]{
            {1, 1},
            {1, 1}
    }),
    S(5, new int[][]{
            {0, 1, 1},
            {1, 1, 0},
            {0, 0, 0}
    }),
    T(6, new int[][]{
            {0, 1, 0},
            {1, 1, 1},
            {0, 0, 0}
    }),
    Z(7, new int[][]{
            {1, 1, 0},
            {0, 1, 1},
            {0, 0, 0}
    });

    private final int id;
    private final int[][] shape;

    TetrominoType(int id, int[][] shape) {
        this.id = id;
        this.shape = shape;
    }

    public int getId() { return id; }
    public int[][] getShape() {
        int[][] copy = new int[shape.length][];
        for (int i = 0; i < shape.length; i++) {
            copy[i] = shape[i].clone();
        }
        return copy;
    }
}
