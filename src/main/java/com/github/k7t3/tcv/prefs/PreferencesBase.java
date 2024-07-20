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

package com.github.k7t3.tcv.prefs;

import javafx.beans.property.*;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;
import java.util.prefs.Preferences;

public abstract class PreferencesBase {

    protected final Preferences preferences;

    protected final Map<String, Object> defaults;

    PreferencesBase(Preferences preferences, Map<String, Object> defaults) {
        this.preferences = preferences;
        this.defaults = defaults;
    }

    /**
     * ストアに保存される内容を明示的に読み込む
     */
    protected abstract void readFromPreferences();

    /**
     * バッファリングされる設定内容を明示的にバッキングストアに書き込む
     */
    protected abstract void writeToPreferences();

    protected BooleanProperty createBooleanProperty(String key) {
        var property = new SimpleBooleanProperty(getBoolean(key));
        property.addListener((ob, o, n) -> preferences.putBoolean(key, n));
        return property;
    }

    protected IntegerProperty createIntegerProperty(String key) {
        var property = new SimpleIntegerProperty(getInt(key));
        property.addListener((ob, o, n) -> preferences.putInt(key, n.intValue()));
        return property;
    }

    protected DoubleProperty createDoubleProperty(String key) {
        var property = new SimpleDoubleProperty(getDouble(key));
        property.addListener((ob, o, n) -> preferences.putDouble(key, n.doubleValue()));
        return property;
    }

    protected StringProperty createStringProperty(String key) {
        var property = new SimpleStringProperty(get(key));
        property.addListener((ob, o, n) -> preferences.put(key, n));
        return property;
    }

    protected <T> ObjectProperty<T> createBytesObjectProperty(
            String key,
            Function<byte[], T> fromBytes,
            Function<T, byte[]> toBytes) {
        var property = new SimpleObjectProperty<>(fromBytes.apply(getByteArray(key)));
        property.addListener((ob, o, n) -> preferences.putByteArray(key, toBytes.apply(n)));
        return property;
    }

    protected <T> ObjectProperty<T> createObjectProperty(
            String key,
            Function<String, T> fromString,
            Function<T, String> toString
    ) {
        Function<String, T> f = s -> {
            try {
                return fromString.apply(s);
            } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn(e.getMessage());
                return fromString.apply((String) defaults.get(key));
            }
        };
        var property = new SimpleObjectProperty<>(f.apply(get(key)));
        property.addListener((ob, o, n) -> preferences.put(key, toString.apply(n)));
        return property;
    }

    protected String get(String key) {
        try {
            return preferences.get(key, (String) defaults.get(key));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn(e.getMessage(), e);
            return (String) defaults.get(key);
        }
    }

    protected int getInt(String key) {
        try {
            return preferences.getInt(key, (Integer) defaults.get(key));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn(e.getMessage(), e);
            return (Integer) defaults.get(key);
        }
    }

    protected double getDouble(String key) {
        try {
            return preferences.getDouble(key, (Double) defaults.get(key));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn(e.getMessage(), e);
            return (Double) defaults.get(key);
        }
    }

    protected boolean getBoolean(String key) {
        try {
            return preferences.getBoolean(key, (Boolean) defaults.get(key));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn(e.getMessage(), e);
            return (Boolean) defaults.get(key);
        }
    }

    protected byte[] getByteArray(String key) {
        try {
            return preferences.getByteArray(key, (byte[]) defaults.get(key));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn(e.getMessage(), e);
            return (byte[]) defaults.get(key);
        }
    }

}
