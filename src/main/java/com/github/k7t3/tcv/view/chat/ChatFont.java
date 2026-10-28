/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.view.chat;

import javafx.beans.property.*;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.github.k7t3.tcv.prefs.FontSettings;
import java.util.Objects;

public class ChatFont {

    private static ChatFont DEFAULT = null;

    private static final double DEFAULT_FONT_SIZE = Font.getDefault().getSize();

    private final ReadOnlyStringWrapper family;

    private final ReadOnlyObjectWrapper<Font> font;

    private ReadOnlyObjectWrapper<Font> boldFont;

    private final ReadOnlyDoubleWrapper size;

    private final ObservableDoubleValue fontScale;

    public ChatFont(String family, double size) {
        new FontSettings(family, size); // データ境界と同じ検証を表示モデルにも適用する。
        this.family = new ReadOnlyStringWrapper(family);
        this.font = new ReadOnlyObjectWrapper<>(Font.font(family, size));
        this.size = new ReadOnlyDoubleWrapper(size);
        this.fontScale = this.size.divide(DEFAULT_FONT_SIZE);
    }

    private ChatFont() {
        this.font = new ReadOnlyObjectWrapper<>(Font.getDefault());
        this.family = new ReadOnlyStringWrapper(Font.getDefault().getFamily());
        this.size = new ReadOnlyDoubleWrapper(DEFAULT_FONT_SIZE);
        this.fontScale = this.size.divide(DEFAULT_FONT_SIZE);
    }

    public static synchronized ChatFont getDefault() {
        if (DEFAULT == null) {
            DEFAULT = new ChatFont();
        }
        return DEFAULT;
    }

    public byte[] serialize() { return new FontSettings(getFamily(), getSize()).serialize(); }

    public static ChatFont deserialize(byte[] bytes) {
        var settings = FontSettings.deserialize(bytes);
        return new ChatFont(settings.family(), settings.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatFont chatFont)) return false;
        return Objects.equals(getFamily(), chatFont.getFamily()) && Objects.equals(getSize(), chatFont.getSize());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFamily(), getSize());
    }

    @Override
    public String toString() {
        return "ChatFont{" +
                "font=" + font.get() +
                '}';
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyStringProperty familyProperty() { return family.getReadOnlyProperty(); }
    public String getFamily() { return family.get(); }

    public ReadOnlyObjectProperty<Font> fontProperty() { return font.getReadOnlyProperty(); }
    public Font getFont() { return font.get(); }

    private ReadOnlyObjectWrapper<Font> boldFontWrapper() {
        if (boldFont == null) boldFont = new ReadOnlyObjectWrapper<>(Font.font(getFamily(), FontWeight.BOLD, getSize()));
        return boldFont;
    }
    public ReadOnlyObjectProperty<Font> boldFontProperty() { return boldFontWrapper().getReadOnlyProperty(); }
    public Font getBoldFont() { return boldFontWrapper().get(); }

    public ReadOnlyDoubleProperty sizeProperty() { return size.getReadOnlyProperty(); }
    public double getSize() { return size.get(); }

    public ObservableDoubleValue observableFontScale() { return fontScale; }
    public double getFontScale() { return fontScale.get(); }

}
