package com.github.k7t3.tcv.prefs;

import java.util.Optional;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import org.slf4j.LoggerFactory;

/** キー、型、既定値、保存形式を一か所に定義する。読取障害を不正な値と混同しない。 */
record PreferenceKey<T>(String name, Supplier<T> fallback, Function<String, T> parse, Function<T, String> format) {
    T read(Preferences preferences) {
        // 保存先の SecurityException / IllegalStateException 等は既定値に置き換えず呼出元へ返す。
        return read(Optional.ofNullable(preferences.get(name, null)));
    }

    T read(Optional<String> stored) {
        if (stored.isEmpty()) return fallback.get();
        try { return decode(stored.get()); }
        catch (IllegalArgumentException invalid) {
            // 値や例外メッセージは記録しない。保存値はそのまま保つ。
            LoggerFactory.getLogger(PreferenceKey.class).warn("invalid preference '{}'; using its default", name);
            return fallback.get();
        }
    }

    T decode(String value) {
        var decoded = parse.apply(value);
        if (decoded == null) throw new IllegalArgumentException("invalid preference: " + name);
        return decoded;
    }

    String encode(T value) {
        if (value == null) throw new IllegalArgumentException("invalid preference: " + name);
        var encoded = format.apply(value);
        decode(encoded);
        return encoded;
    }

    static PreferenceKey<Boolean> bool(String name, boolean fallback) {
        return new PreferenceKey<>(name, () -> fallback, value -> {
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
            throw new IllegalArgumentException("invalid boolean preference");
        }, Object::toString);
    }

    static PreferenceKey<Integer> integer(String name, int fallback, IntPredicate valid) {
        return new PreferenceKey<>(name, () -> fallback, value -> {
            var number = Integer.parseInt(value);
            if (!valid.test(number)) throw new IllegalArgumentException("invalid integer preference");
            return number;
        }, Object::toString);
    }

    static PreferenceKey<Double> decimal(String name, double fallback, DoublePredicate valid) {
        return new PreferenceKey<>(name, () -> fallback, value -> {
            var number = Double.parseDouble(value);
            if (!valid.test(number)) throw new IllegalArgumentException("invalid decimal preference");
            return number;
        }, Object::toString);
    }

    static <E extends Enum<E>> PreferenceKey<E> enumeration(String name, E fallback, Class<E> type) {
        return new PreferenceKey<>(name, () -> fallback, value -> Enum.valueOf(type, value), Enum::name);
    }
}
