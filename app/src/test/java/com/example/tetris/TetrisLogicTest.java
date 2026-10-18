package com.example.tetris;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.tetris.layout.ScreenLayout;
import com.example.tetris.logic.Board;
import com.example.tetris.logic.TetrisGame;
import com.example.tetris.logic.Tetromino;
import com.example.tetris.logic.TetrominoType;
import com.example.tetris.ui.ButtonId;
import com.example.tetris.ui.ScreenFlow;

import java.util.Random;

public class TetrisLogicTest {

    @Test
    public void testBoardPlacementAndLockout() {
        Board board = new Board();
        Tetromino piece = new Tetromino(TetrominoType.O); // 2x2
        piece.setX(4);
        piece.setY(10);
        assertTrue("Piece inside board should place completely", board.placeTetromino(piece));

        int[][] grid = board.getGrid();
        assertEquals(TetrominoType.O.getId(), grid[10][4]);
        assertEquals(TetrominoType.O.getId(), grid[11][5]);

        // Place a piece partially above the board (py = -1)
        Tetromino highPiece = new Tetromino(TetrominoType.O);
        highPiece.setX(0);
        highPiece.setY(-1);
        assertFalse("Piece with y < 0 should not place all blocks", board.placeTetromino(highPiece));
    }

    @Test
    public void testBoardClearFullLines() {
        Board board = new Board();
        int[][] grid = board.getGrid();

        // Fill bottom row
        for (int c = 0; c < Board.WIDTH; c++) {
            grid[Board.HEIGHT - 1][c] = 1;
        }

        assertEquals(1, board.clearFullLines());

        // Ensure bottom row is now empty
        for (int c = 0; c < Board.WIDTH; c++) {
            assertEquals(0, grid[Board.HEIGHT - 1][c]);
        }
    }

    @Test
    public void testTetrisGameWallKick() {
        TetrisGame game = new TetrisGame(new Random(42));
        Tetromino current = game.getCurrentPiece();
        assertNotNull(current);

        // Move to right wall
        for (int i = 0; i < 10; i++) {
            game.moveRight();
        }

        // Rotate against wall should either succeed via kick or rollback cleanly
        int oldX = current.getX();
        game.rotate();
        assertTrue(game.getBoard().isValidPosition(current, current.getX(), current.getY()));
    }

    @Test
    public void testTetrisGameHoldPiece() {
        TetrisGame game = new TetrisGame(new Random(42));
        TetrominoType firstPiece = game.getCurrentPiece().getType();

        game.hold();
        assertEquals(firstPiece, game.getHoldPiece());

        // Cannot hold twice in a row before piece lock
        TetrominoType secondPiece = game.getCurrentPiece().getType();
        game.hold();
        assertEquals(firstPiece, game.getHoldPiece());
        assertEquals(secondPiece, game.getCurrentPiece().getType());
    }

    @Test
    public void testScreenFlowBackNavigation() {
        ScreenFlow flow = new ScreenFlow();
        assertEquals(ScreenFlow.Screen.MAIN_MENU, flow.screen());

        // Back from main menu exits app
        assertEquals(ScreenFlow.Effect.EXIT_APP, flow.onBack());

        // Start game
        assertEquals(ScreenFlow.Effect.START_NEW_GAME, flow.onButton(ButtonId.PLAY));
        assertEquals(ScreenFlow.Screen.PLAYING, flow.screen());

        // Back from playing pauses game
        assertEquals(ScreenFlow.Effect.PAUSE_GAME, flow.onBack());
        assertEquals(ScreenFlow.Screen.PAUSED, flow.screen());

        // Back from pause goes to MAIN_MENU (breaks infinite loop trap)
        assertEquals(ScreenFlow.Effect.SHOW_MAIN_MENU, flow.onBack());
        assertEquals(ScreenFlow.Screen.MAIN_MENU, flow.screen());

        // Back from main menu exits app
        assertEquals(ScreenFlow.Effect.EXIT_APP, flow.onBack());
    }

    @Test
    public void testScreenFlowGameOverBack() {
        ScreenFlow flow = new ScreenFlow();
        flow.onButton(ButtonId.PLAY);
        assertEquals(ScreenFlow.Screen.PLAYING, flow.screen());

        // When Game Over, pressing Back navigates directly to MAIN_MENU
        assertEquals(ScreenFlow.Effect.SHOW_MAIN_MENU, flow.onGameOverBack());
        assertEquals(ScreenFlow.Screen.MAIN_MENU, flow.screen());
    }

    @Test
    public void testScreenLayoutNextBoxTapIgnored() {
        // NEXT_BOX is at (168, 24, 208, 64)
        ScreenLayout.TapZone zone = ScreenLayout.zoneAtMaster(180, 40);
        assertEquals(ScreenLayout.TapZone.NONE, zone);

        // HOLD_BOX is at (48, 24, 88, 64)
        ScreenLayout.TapZone holdZone = ScreenLayout.zoneAtMaster(60, 40);
        assertEquals(ScreenLayout.TapZone.HOLD, holdZone);

        // MENU_BUTTON is at (172, 424, 208, 456)
        ScreenLayout.TapZone menuZone = ScreenLayout.zoneAtMaster(190, 440);
        assertEquals(ScreenLayout.TapZone.MENU, menuZone);

        // Main field
        ScreenLayout.TapZone fieldZone = ScreenLayout.zoneAtMaster(100, 200);
        assertEquals(ScreenLayout.TapZone.FIELD, fieldZone);
    }

    @Test
    public void testTetrominoTypeShapeImmutability() {
        int[][] shape = TetrominoType.T.getShape();
        int original = shape[0][0];
        shape[0][0] = 999;

        // Ensure master shape was not modified
        int[][] fresh = TetrominoType.T.getShape();
        assertEquals(original, fresh[0][0]);
    }
}

