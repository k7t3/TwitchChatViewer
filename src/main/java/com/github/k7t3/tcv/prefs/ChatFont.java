package com.github.k7t3.tcv.prefs;

import javafx.scene.text.Font;

import java.util.Objects;

public class ChatFont {

    public static final ChatFont DEFAULT = new ChatFont();

    private final String family;

    private final Font font;

    public ChatFont(String family) {
        this.family = family;
        this.font = Font.font(family);
    }

    private ChatFont() {
        this.font = Font.getDefault();
        this.family = font.getFamily();
    }

    public String getFamily() {
        return family;
    }

    public Font getFont() {
        return font;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatFont chatFont)) return false;
        return Objects.equals(font, chatFont.font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(font);
    }
}
