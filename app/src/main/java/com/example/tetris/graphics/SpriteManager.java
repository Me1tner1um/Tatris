package com.example.tetris.graphics;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.tetris.layout.ScreenLayout;
import com.example.tetris.logic.TetrominoType;
import com.example.tetris.ui.ButtonId;
import com.example.tetris.ui.MenuLayout;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Загружает спрайты из отдельных PNG-файлов в app/src/main/assets/sprites/:
 *   blocks/i.png, j.png, l.png, o.png, s.png, t.png, z.png  - блоки фигур (имя файла = имя TetrominoType, строчными)
 *   ghost.png                                               - клетка проекции (Ghost Piece)
 *   background.png                                          - мастер-фон 256x480: безопасная зона 180x440 в центре, вокруг неё bleed
 *   ui/logo.png                                             - логотип главного меню 160x100
 *   ui/<кнопка>.png и ui/<кнопка>_pressed.png               - кнопки меню 120x36 (_pressed необязателен), имена в ButtonId
 * Если файла нет или его размер неверный, соответствующий геттер возвращает null,
 * а GameRenderer рисует вместо спрайта простую заливку.
 */
public final class SpriteManager {

    // Размеры спрайтов: контракт между файлами в assets и рендерером
    public static final int BLOCK_SIZE = ScreenLayout.CELL_SIZE;
    public static final int BACKGROUND_WIDTH = ScreenLayout.MASTER_WIDTH;
    public static final int BACKGROUND_HEIGHT = ScreenLayout.MASTER_HEIGHT;

    private static final String TAG = "SpriteManager";
    private static final String SPRITES_DIR = "sprites/";
    private static final String BLOCKS_DIR = SPRITES_DIR + "blocks/";
    private static final String GHOST_FILE = SPRITES_DIR + "ghost.png";
    private static final String BACKGROUND_FILE = SPRITES_DIR + "background.png";
    private static final String UI_DIR = SPRITES_DIR + "ui/";
    private static final String LOGO_FILE = UI_DIR + "logo.png";

    // Индекс = TetrominoType.getId() (1..7), элемент 0 не используется
    private static final Bitmap[] blockSprites = new Bitmap[TetrominoType.values().length + 1];
    private static Bitmap ghostSprite;
    private static Bitmap backgroundSprite;
    private static Bitmap logoSprite;
    // Индекс = ButtonId.ordinal()
    private static final Bitmap[] buttonSprites = new Bitmap[ButtonId.values().length];
    private static final Bitmap[] buttonPressedSprites = new Bitmap[ButtonId.values().length];
    private static boolean initialized = false;

    private SpriteManager() {
    }

    // Вызывается из UI-потока до старта игрового потока. Повторные вызовы ничего не делают: битмапы живут весь процесс.
    public static synchronized void init(Context context) {
        if (initialized) return;

        AssetManager assets = context.getApplicationContext().getAssets();

        for (TetrominoType type : TetrominoType.values()) {
            String file = BLOCKS_DIR + type.name().toLowerCase(Locale.ROOT) + ".png";
            blockSprites[type.getId()] = load(assets, file, BLOCK_SIZE, BLOCK_SIZE, true);
        }
        ghostSprite = load(assets, GHOST_FILE, BLOCK_SIZE, BLOCK_SIZE, true);
        backgroundSprite = load(assets, BACKGROUND_FILE, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, true);
        logoSprite = load(assets, LOGO_FILE, MenuLayout.LOGO_WIDTH, MenuLayout.LOGO_HEIGHT, true);
        for (ButtonId id : ButtonId.values()) {
            String base = UI_DIR + id.fileName();
            buttonSprites[id.ordinal()] = load(assets, base + ".png", MenuLayout.BUTTON_WIDTH, MenuLayout.BUTTON_HEIGHT, true);
            // Вариант для нажатой кнопки необязателен: без него обычный спрайт просто сдвигается на пиксель
            buttonPressedSprites[id.ordinal()] = load(assets, base + "_pressed.png", MenuLayout.BUTTON_WIDTH, MenuLayout.BUTTON_HEIGHT, false);
        }

        initialized = true;
    }

    // Читает PNG без масштабирования под плотность экрана. При любой проблеме возвращает null и пишет причину в лог.
    // required=false: отсутствие файла не считается проблемой и в лог не пишется (размер неверного файла всё равно попадёт в лог)
    private static Bitmap load(AssetManager assets, String path, int expectedWidth, int expectedHeight, boolean required) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream in = assets.open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) {
                Log.w(TAG, path + ": не удалось декодировать изображение");
                return null;
            }
            if (bitmap.getWidth() != expectedWidth || bitmap.getHeight() != expectedHeight) {
                Log.w(TAG, path + ": размер " + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + ", а нужен " + expectedWidth + "x" + expectedHeight);
                bitmap.recycle();
                return null;
            }
            return bitmap;
        } catch (FileNotFoundException e) {
            if (required) {
                Log.w(TAG, path + ": файл не найден");
            }
        } catch (IOException e) {
            Log.w(TAG, path + ": ошибка чтения", e);
        }
        return null;
    }

    public static Bitmap getBlockSprite(int id) {
        if (id >= 1 && id < blockSprites.length) return blockSprites[id];
        return null;
    }

    public static Bitmap getGhostSprite() {
        return ghostSprite;
    }

    public static Bitmap getBackgroundSprite() {
        return backgroundSprite;
    }

    public static Bitmap getLogoSprite() {
        return logoSprite;
    }

    public static Bitmap getButtonSprite(ButtonId id) {
        return buttonSprites[id.ordinal()];
    }

    // null, если отдельного спрайта нажатой кнопки нет
    public static Bitmap getButtonPressedSprite(ButtonId id) {
        return buttonPressedSprites[id.ordinal()];
    }
}
