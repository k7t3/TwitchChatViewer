package com.github.k7t3.tcv.prefs;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Objects;

public class ChatFont {

    public static final ChatFont DEFAULT = new ChatFont();

    private final String family;

    private final Font font;

    private Font boldFont;

    public ChatFont(String family) {
        this.family = family;
        this.font = Font.font(family);
        boldFont = null;
    }

    private ChatFont() {
        this.font = Font.getDefault();
        this.family = font.getFamily();
        boldFont = null;
    }

    public String getFamily() {
        return family;
    }

    public Font getFont() {
        return font;
    }

    public Font getBoldFont() {
        if (boldFont == null) {
            boldFont = Font.font(family, FontWeight.BOLD, -1.0);
        }
        return boldFont;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatFont chatFont)) return false;
        return Objects.equals(family, chatFont.family);
    }

    @Override
    public int hashCode() {
        return Objects.hash(family);
    }
}
