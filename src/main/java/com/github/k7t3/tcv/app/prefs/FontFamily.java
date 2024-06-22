package com.github.k7t3.tcv.app.prefs;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.text.Font;

public class FontFamily {

    private final ReadOnlyStringWrapper family;
    private final ReadOnlyObjectWrapper<Font> font;

    public FontFamily(String family) {
        this.family = new ReadOnlyStringWrapper(family);
        this.font = new ReadOnlyObjectWrapper<>(Font.font(family));
    }

    public ReadOnlyStringProperty familyProperty() { return family.getReadOnlyProperty(); }
    public String getFamily() { return family.get(); }

    public ReadOnlyObjectProperty<Font> fontProperty() { return font.getReadOnlyProperty(); }
    public Font getFont() { return font.get(); }

}
