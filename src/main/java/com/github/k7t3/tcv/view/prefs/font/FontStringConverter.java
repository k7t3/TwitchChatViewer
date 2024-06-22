package com.github.k7t3.tcv.view.prefs.font;

import com.github.k7t3.tcv.app.prefs.FontFamily;
import javafx.util.StringConverter;

import java.util.List;

public class FontStringConverter extends StringConverter<FontFamily> {

    private final List<FontFamily> fonts;

    public FontStringConverter(List<FontFamily> fonts) {
        this.fonts = fonts;
    }

    @Override
    public String toString(FontFamily font) {
        return font == null ? "" : font.getFamily();
    }

    @Override
    public FontFamily fromString(String name) {
        return fonts.stream()
                .filter(f -> f.getFamily().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
