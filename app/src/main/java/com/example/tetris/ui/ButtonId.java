package com.example.tetris.ui;

/** Кнопки меню. fileName - имя файла спрайта в assets/sprites/ui/ (без расширения), label - подпись для запасной отрисовки. */
public enum ButtonId {
    PLAY("play", "PLAY"),
    SETTINGS("settings", "SETTINGS"),
    EXIT("exit", "EXIT"),
    RESUME("resume", "RESUME"),
    MAIN_MENU("main_menu", "MAIN MENU");

    private final String fileName;
    private final String label;

    ButtonId(String fileName, String label) {
        this.fileName = fileName;
        this.label = label;
    }

    public String fileName() { return fileName; }

    public String label() { return label; }
}
