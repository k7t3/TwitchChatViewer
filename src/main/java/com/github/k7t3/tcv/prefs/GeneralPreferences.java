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

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.core.OS;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.database.DatabaseVersion;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class GeneralPreferences extends PreferencesBase {

    private static final PreferenceKey<Theme> THEME = PreferenceKey.enumeration("theme", ThemeManager.DEFAULT_THEME, Theme.class);
    private static final PreferenceKey<ThemeType> THEME_TYPE = PreferenceKey.enumeration("theme.type", ThemeType.SYSTEM, ThemeType.class);
    private static final PreferenceKey<MultipleChatOpenType> MULTIPLE_OPEN_TYPE = PreferenceKey.enumeration(
            "multiple.open.type", MultipleChatOpenType.SEPARATED, MultipleChatOpenType.class);
    private static final String DATABASE_VERSION = "database.version";
    private static final PreferenceKey<String> USER_DATA_FILE_PATH = new PreferenceKey<>("user.data.file",
            () -> OS.current().getApplicationDirectory().toAbsolutePath().resolve("userdata.db").toString(),
            java.util.function.Function.identity(), java.util.function.Function.identity());

    static Map<String, PreferenceKey<?>> editorSettings() {
        return Map.of(THEME.name(), THEME, THEME_TYPE.name(), THEME_TYPE, MULTIPLE_OPEN_TYPE.name(), MULTIPLE_OPEN_TYPE);
    }

    static java.util.Set<String> storedKeys() {
        var keys = new java.util.HashSet<>(editorSettings().keySet());
        keys.add(DATABASE_VERSION);
        keys.add(USER_DATA_FILE_PATH.name());
        return java.util.Set.copyOf(keys);
    }

    public static Map<String, Optional<String>> editorValues(Theme theme, ThemeType type, MultipleChatOpenType open) {
        return Map.of(THEME.name(), Optional.of(THEME.encode(theme)), THEME_TYPE.name(), Optional.of(THEME_TYPE.encode(type)),
                MULTIPLE_OPEN_TYPE.name(), Optional.of(MULTIPLE_OPEN_TYPE.encode(open)));
    }

    private ObjectProperty<Theme> theme;
    private ObjectProperty<ThemeType> themeType;

    private ObjectProperty<MultipleChatOpenType> multipleOpenType;

    private StringProperty userDataFilePath;

    GeneralPreferences(Preferences preferences) { super(preferences, editorSettings().keySet()); }
    GeneralPreferences(PreferenceStore store) { super(store, editorSettings().keySet()); }

    public void setDatabaseVersion(DatabaseVersion version) {
        if (version == DatabaseVersion.EMPTY || version == DatabaseVersion.UNKNOWN) {
            throw new IllegalArgumentException();
        }
        store.write(DATABASE_VERSION, Optional.of(version.name()), false);
    }

    public DatabaseVersion getDatabaseVersion() {
        var name = store.read(DATABASE_VERSION).orElse(DatabaseVersion.EMPTY.name());
        return DatabaseVersion.valueOf(name);
    }


    // ******************** PROPERTIES ********************

    public ObjectProperty<Theme> themeProperty() {
        if (theme == null) {
            theme = createObjectProperty(THEME);
        }
        return theme;
    }
    public Theme getTheme() { return themeProperty().get(); }
    public void setTheme(Theme theme) { themeProperty().set(theme); }

    public ObjectProperty<ThemeType> themeTypeProperty() {
        if (themeType == null) {
            themeType = createObjectProperty(THEME_TYPE);
        }
        return themeType;
    }
    public ThemeType getThemeType() { return themeTypeProperty().get(); }
    public void setThemeType(ThemeType themeType) { themeTypeProperty().set(themeType); }

    public ObjectProperty<MultipleChatOpenType> multipleOpenTypeProperty() {
        if (multipleOpenType == null) {
            multipleOpenType = createObjectProperty(MULTIPLE_OPEN_TYPE);
        }
        return multipleOpenType;
    }
    public MultipleChatOpenType getMultipleOpenType() { return multipleOpenTypeProperty().get(); }
    public void setMultipleOpenType(MultipleChatOpenType multipleOpenType) { multipleOpenTypeProperty().set(multipleOpenType); }

    public StringProperty userDataFilePathProperty() {
        if (userDataFilePath == null) userDataFilePath = createStringProperty(USER_DATA_FILE_PATH);
        return userDataFilePath;
    }
    public String getUserDataFilePath() { return userDataFilePathProperty().get(); }
    public void setUserDataFilePath(String userDataFilePath) { userDataFilePathProperty().set(userDataFilePath); }

}
