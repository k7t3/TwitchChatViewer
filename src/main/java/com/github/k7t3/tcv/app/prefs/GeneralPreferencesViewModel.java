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

package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.beans.value.ChangeListener;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;


public class GeneralPreferencesViewModel implements PreferencesViewModelBase {

    private final GeneralPreferences prefs;
    private final ThemeManager preview;
    private boolean closed;
    private final ChangeListener<Theme> themeChanged;
    private final ChangeListener<ThemeType> typeChanged;

    private final ObjectProperty<Theme> theme;
    private final ObjectProperty<ThemeType> themeType;

    private final ObjectProperty<MultipleChatOpenType> chatOpenType;

    public GeneralPreferencesViewModel(GeneralPreferences prefs) {
        this(prefs, null);
    }

    public GeneralPreferencesViewModel(GeneralPreferences prefs, ThemeManager preview) {
        this.prefs = prefs;
        this.preview = preview;
        themeChanged = (observable, previous, next) -> { if (!closed && preview != null) preview.setTheme(next); };
        typeChanged = (observable, previous, next) -> { if (!closed && preview != null) preview.setThemeType(next); };
        theme = new SimpleObjectProperty<>(prefs.getTheme());
        themeType = new SimpleObjectProperty<>(prefs.getThemeType());
        chatOpenType = new SimpleObjectProperty<>(prefs.getMultipleOpenType());
        theme.addListener(themeChanged);
        themeType.addListener(typeChanged);
    }

    @Override
    public boolean canSync() {
        return !closed && getTheme() != null && getThemeType() != null && getChatOpenType() != null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> {
            theme.removeListener(themeChanged);
            themeType.removeListener(typeChanged);
        }, () -> { if (preview != null) preview.setTheme(prefs.getTheme()); },
                () -> { if (preview != null) preview.setThemeType(prefs.getThemeType()); });
    }

    @Override
    public void prepare(SettingsDraft.Builder target) {
        if (!canSync()) throw new IllegalStateException("general preferences contain an incomplete choice");
        target.values(GeneralPreferences.editorValues(getTheme(), getThemeType(), getChatOpenType()));
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<Theme> themeProperty() { return theme; }
    public Theme getTheme() { return theme.get(); }
    public void setTheme(Theme theme) { this.theme.set(theme); }

    public ObjectProperty<ThemeType> themeTypeProperty() { return themeType; }
    public ThemeType getThemeType() { return themeType.get(); }
    public void setThemeType(ThemeType themeType) { this.themeType.set(themeType); }

    public ObjectProperty<MultipleChatOpenType> chatOpenTypeProperty() { return chatOpenType; }
    public MultipleChatOpenType getChatOpenType() { return chatOpenType.get(); }
    public void setChatOpenType(MultipleChatOpenType chatOpenType) { this.chatOpenType.set(chatOpenType); }

}
