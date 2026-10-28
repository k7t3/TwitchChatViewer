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

package com.github.k7t3.tcv.app.theme;

import com.github.k7t3.tcv.app.core.OS;
import java.util.Objects;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// AtlantaFX samplerアプリケーションを参照
public class ThemeManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThemeManager.class);

    public static final Theme DEFAULT_THEME;

    static {
        if (OS.isMac())
            DEFAULT_THEME = Theme.CUPERTINO;
        else
            DEFAULT_THEME = Theme.PRIMER;
    }

    private final ObjectProperty<ThemeType> themeType = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            var type = get();
            var theme = getTheme();
            updateTheme(theme, type);
        }

        @Override
        public Object getBean() {
            return ThemeManager.this;
        }

        @Override
        public String getName() {
            return "themeType";
        }
    };

    private final ObjectProperty<Theme> theme = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            var theme = get();
            var type = getThemeType();
            updateTheme(theme, type);
        }

        @Override
        public Object getBean() {
            return ThemeManager.this;
        }

        @Override
        public String getName() {
            return "theme";
        }
    };

    private Scene scene;

    // テーマをシステムと同期するときに使用するリスナ
    private ChangeListener<ColorScheme> systemColorSchemeListener = null;

    private boolean initialized = false;

    private boolean closed;
    private long themeRevision;
    private final ObservableValue<ColorScheme> colorScheme;

    public ThemeManager() {
        this(Platform.getPreferences().colorSchemeProperty());
    }

    ThemeManager(ObservableValue<ColorScheme> colorScheme) { this.colorScheme = Objects.requireNonNull(colorScheme); }

    public void setScene(Scene scene) {
        if (closed) throw new IllegalStateException("theme manager is closed");
        this.scene = scene;
    }

    private void updateTheme(Theme theme, ThemeType type) {
        if (closed || scene == null || theme == null || type == null) return;
        var revision = ++themeRevision;

        LOGGER.info("updated theme {}:{}", theme, type);
        if (systemColorSchemeListener != null) {
            colorScheme.removeListener(systemColorSchemeListener);
            systemColorSchemeListener = null;
        }
        switch (type) {
            case LIGHTER, DARKER -> theme.applyTheme(scene, type, initialized);
            case SYSTEM -> systemTheme(theme, revision);
        }
        if (closed || revision != themeRevision) return;
        initialized = true;
    }

    private void systemTheme(Theme theme, long revision) {
        updateSystemColorScheme(theme, colorScheme.getValue());
        // 色の反映通知から設定変更や終了へ再入した場合、古い適用から購読を追加しない。
        if (closed || revision != themeRevision) return;
        systemColorSchemeListener = (ob, o, n) -> updateSystemColorScheme(theme, n);
        colorScheme.addListener(systemColorSchemeListener);
    }

    private void updateSystemColorScheme(Theme theme, ColorScheme colorScheme) {
        switch (colorScheme) {
            case LIGHT -> theme.applyTheme(scene, ThemeType.LIGHTER, initialized);
            case DARK -> theme.applyTheme(scene, ThemeType.DARKER, initialized);
        }
    }

    public ObjectProperty<ThemeType> themeTypeProperty() { return themeType; }
    public ThemeType getThemeType() { return themeTypeProperty().get(); }
    public void setThemeType(ThemeType type) { themeTypeProperty().set(type); }

    public ObjectProperty<Theme> themeProperty() { return theme; }
    public Theme getTheme() { return themeProperty().get(); }
    public void setTheme(Theme theme) { this.theme.set(theme); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (systemColorSchemeListener != null) {
            colorScheme.removeListener(systemColorSchemeListener);
            systemColorSchemeListener = null;
        }
        scene = null;
        initialized = false;
    }

}
