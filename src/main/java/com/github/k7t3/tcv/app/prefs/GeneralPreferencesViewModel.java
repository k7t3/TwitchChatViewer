package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

public class GeneralPreferencesViewModel implements PreferencesViewModelBase {

    private final GeneralPreferences prefs;

    private final ReadOnlyObjectWrapper<Theme> defaultTheme;
    private final ReadOnlyObjectWrapper<ThemeType> defaultThemeType;

    private final ObjectProperty<Theme> theme;
    private final ObjectProperty<ThemeType> themeType;

    private final ObjectProperty<MultipleChatOpenType> chatOpenType;

    private final StringProperty userDataFilePath;

    public GeneralPreferencesViewModel(GeneralPreferences prefs) {
        this.prefs = prefs;
        defaultTheme = new ReadOnlyObjectWrapper<>(prefs.getTheme());
        defaultThemeType = new ReadOnlyObjectWrapper<>(prefs.getThemeType());
        theme = new SimpleObjectProperty<>(prefs.getTheme());
        themeType = new SimpleObjectProperty<>(prefs.getThemeType());
        chatOpenType = new SimpleObjectProperty<>(prefs.getMultipleOpenType());
        userDataFilePath = new SimpleStringProperty(prefs.getUserDataFilePath());
    }

    @Override
    public boolean canSync() {
        return true;
    }

    @Override
    public void sync() {
        var theme = getTheme();
        if (!Objects.equals(getDefaultTheme(), theme)) {
            prefs.setTheme(theme);
        }

        var themeType = getThemeType();
        if (!Objects.equals(getDefaultThemeType(), themeType)) {
            prefs.setThemeType(themeType);
        }

        var openType = getChatOpenType();
        if (openType != prefs.getMultipleOpenType()) {
            prefs.setMultipleOpenType(openType);
        }

        var userData = getUserDataFilePath();
        if (!Objects.equals(userData, prefs.getUserDataFilePath())) {
            prefs.setUserDataFilePath(userData);
        }
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<Theme> defaultThemeProperty() { return defaultTheme.getReadOnlyProperty(); }
    public Theme getDefaultTheme() { return defaultTheme.get(); }

    public ReadOnlyObjectProperty<ThemeType> defaultThemeTypeProperty() { return defaultThemeType.getReadOnlyProperty(); }
    public ThemeType getDefaultThemeType() { return defaultThemeType.get(); }

    public ObjectProperty<Theme> themeProperty() { return theme; }
    public Theme getTheme() { return theme.get(); }
    public void setTheme(Theme theme) { this.theme.set(theme); }

    public ObjectProperty<ThemeType> themeTypeProperty() { return themeType; }
    public ThemeType getThemeType() { return themeType.get(); }
    public void setThemeType(ThemeType themeType) { this.themeType.set(themeType); }

    public ObjectProperty<MultipleChatOpenType> chatOpenTypeProperty() { return chatOpenType; }
    public MultipleChatOpenType getChatOpenType() { return chatOpenType.get(); }
    public void setChatOpenType(MultipleChatOpenType chatOpenType) { this.chatOpenType.set(chatOpenType); }

    public StringProperty userDataFilePathProperty() { return userDataFilePath; }
    public String getUserDataFilePath() { return userDataFilePath.get(); }
    public void setUserDataFilePath(String userDataFilePath) { userDataFilePathProperty().set(userDataFilePath); }

}
