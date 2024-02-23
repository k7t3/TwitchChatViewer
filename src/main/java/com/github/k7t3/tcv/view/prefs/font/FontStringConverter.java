package com.github.k7t3.tcv.view.prefs.font;

import com.github.k7t3.tcv.prefs.ChatFont;
import javafx.util.StringConverter;

import java.util.List;

public class FontStringConverter extends StringConverter<ChatFont> {

    private final List<ChatFont> fonts;

    public FontStringConverter(List<ChatFont> fonts) {
        this.fonts = fonts;
    }

    @Override
    public String toString(ChatFont font) {
        return font == null ? "" : font.getFont().getName();
    }

    @Override
    public ChatFont fromString(String name) {
        return fonts.stream()
                .filter(f -> f.getFamily().equals(name))
                .findFirst()
                .orElse(ChatFont.DEFAULT);
    }
}
