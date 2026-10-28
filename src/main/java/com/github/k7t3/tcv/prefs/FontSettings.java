package com.github.k7t3.tcv.prefs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** 保存形式は従来の UTF フォント名 + double サイズ。JavaFX の表示状態を持たない。 */
public record FontSettings(String family, double size) {
    public FontSettings {
        if (family == null || family.isBlank() || !Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("invalid font settings");
        }
    }

    public byte[] serialize() {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeUTF(family);
            out.writeDouble(size);
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalArgumentException("cannot encode font settings", failure); }
    }

    public static FontSettings deserialize(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("missing font settings");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var value = new FontSettings(in.readUTF(), in.readDouble());
            if (in.read() != -1) throw new IllegalArgumentException("trailing font settings data");
            return value;
        } catch (IOException failure) { throw new IllegalArgumentException("cannot decode font settings", failure); }
    }
}
