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
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// AtlantaFX samplerアプリケーションを参照
public class ThemeManager {

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

    private ThemeManager() {
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    private void updateTheme(Theme theme, ThemeType type) {
        if (theme == null || type == null) return;

        LOGGER.info("updated theme {}:{}", theme, type);
        if (systemColorSchemeListener != null) {
            var preferences = Platform.getPreferences();
            preferences.colorSchemeProperty().removeListener(systemColorSchemeListener);
            systemColorSchemeListener = null;
        }
        switch (type) {
            case LIGHTER, DARKER -> theme.applyTheme(scene, type, initialized);
            case SYSTEM -> systemTheme(theme);
        }
        initialized = true;
    }

    private void systemTheme(Theme theme) {
        var preferences = Platform.getPreferences();
        var colorScheme = preferences.getColorScheme();
        updateSystemColorScheme(theme, colorScheme);
        systemColorSchemeListener = (ob, o, n) -> updateSystemColorScheme(theme, n);
        preferences.colorSchemeProperty().addListener(systemColorSchemeListener);
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

    public static ThemeManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final ThemeManager INSTANCE = new ThemeManager();
    }

}
