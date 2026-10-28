package com.github.k7t3.tcv.prefs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.prefs.Preferences;

/** 設定の復旧記録とキー所有情報が共有する、既存の版付きバイナリ形式。 */
public final class PreferenceValuesCodec {
    private PreferenceValuesCodec() {}

    /**
     * キー順の版1形式へ変換する。emptyは値の削除を表す。
     * @param values Preferencesの長さ制約に収まるキーとraw値
     * @return 既存の復旧記録と互換のバイト列
     */
    public static byte[] encode(Map<String, Optional<String>> values) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(1);
                output.writeInt(values.size());
                for (var entry : new TreeMap<>(values).entrySet()) {
                    validateLength(entry.getKey(), entry.getValue());
                    output.writeUTF(entry.getKey());
                    output.writeBoolean(entry.getValue().isPresent());
                    if (entry.getValue().isPresent()) output.writeUTF(entry.getValue().orElseThrow());
                }
            }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /**
     * 版・件数・重複・長さ・末尾を検証して読み戻す。
     * @param bytes 版1形式の記録
     * @return 不変のraw値。型ごとの妥当性は利用側で検証する
     * @throws IllegalArgumentException 記録が不正、または未対応の版の場合
     */
    public static Map<String, Optional<String>> decode(byte[] bytes) {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 1) throw new IllegalArgumentException("unknown settings recovery format");
            var count = input.readInt();
            if (count < 0 || count > bytes.length / 3) throw new IllegalArgumentException("invalid settings recovery size");
            var result = new HashMap<String, Optional<String>>();
            for (var index = 0; index < count; index++) {
                var key = input.readUTF();
                var value = input.readBoolean() ? Optional.of(input.readUTF()) : Optional.<String>empty();
                validateLength(key, value);
                if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("duplicate recovery key");
            }
            if (input.read() != -1) throw new IllegalArgumentException("trailing settings recovery data");
            return Map.copyOf(result);
        } catch (IOException failure) { throw new IllegalArgumentException("invalid settings recovery record", failure); }
    }

    private static void validateLength(String key, Optional<String> value) {
        if (key.length() > Preferences.MAX_KEY_LENGTH || value.filter(s -> s.length() > Preferences.MAX_VALUE_LENGTH).isPresent()) {
            throw new IllegalArgumentException("invalid settings recovery value length");
        }
    }
}
