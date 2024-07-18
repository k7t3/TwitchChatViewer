package com.github.k7t3.tcv.view.chat;

import javafx.beans.property.*;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.*;
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

    public byte[] serialize() {
        try (var bo = new ByteArrayOutputStream();
             var dos = new DataOutputStream(bo)) {
            var family = getFamily();
            var size = getSize();

            dos.writeUTF(family);
            dos.writeDouble(size);

            return bo.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ChatFont deserialize(byte[] bytes) {
        try (var bi = new ByteArrayInputStream(bytes);
             var dis = new DataInputStream(bi)) {

            var family = dis.readUTF();
            var size = dis.readDouble();

            return new ChatFont(family, size);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
