package com.github.k7t3.tcv.view.prefs.font;

import javafx.scene.text.Font;
import javafx.util.StringConverter;

import java.util.List;

public class FontStringConverter extends StringConverter<Font> {

    private final List<Font> fonts;

    public FontStringConverter(List<Font> fonts) {
        this.fonts = fonts;
    }

    @Override
    public String toString(Font font) {
        return font == null ? "" : font.getName();
    }

    @Override
    public Font fromString(String name) {
        return fonts.stream()
                .filter(f -> f.getFamily().equals(name))
                .findFirst()
                .orElse(Font.getDefault());
    }
}
