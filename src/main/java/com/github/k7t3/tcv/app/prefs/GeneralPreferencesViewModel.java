package com.github.k7t3.tcv.app.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import javafx.beans.property.*;

import java.util.Objects;

public class GeneralPreferencesViewModel implements PreferencesViewModelBase {

    private final GeneralPreferences prefs;

    private final ReadOnlyObjectWrapper<Theme> defaultTheme;

    private final ObjectProperty<Theme> theme;

    private final ObjectProperty<MultipleChatOpenType> chatOpenType;

    private final IntegerProperty chatCacheSize;

    public GeneralPreferencesViewModel() {
        this.prefs = AppPreferences.getInstance().getGeneralPreferences();
        defaultTheme = new ReadOnlyObjectWrapper<>(prefs.getTheme());
        theme = new SimpleObjectProperty<>(prefs.getTheme());
        chatOpenType = new SimpleObjectProperty<>(prefs.getMultipleOpenType());
        chatCacheSize = new SimpleIntegerProperty(prefs.getChatCacheSize());
    }

    public void sync() {
        var theme = getTheme();
        if (!Objects.equals(prefs.getTheme().getName(), theme.getName())) {
            prefs.setTheme(theme);
        }

        var openType = getChatOpenType();
        if (openType != prefs.getMultipleOpenType()) {
            prefs.setMultipleOpenType(openType);
        }

        var cacheSize = getChatCacheSize();
        if (cacheSize != prefs.getChatCacheSize()) {
            prefs.setChatCacheSize(cacheSize);
        }
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<Theme> defaultThemeProperty() { return defaultTheme.getReadOnlyProperty(); }
    public Theme getDefaultTheme() { return defaultTheme.get(); }

    public ObjectProperty<Theme> themeProperty() { return theme; }
    public Theme getTheme() { return theme.get(); }
    public void setTheme(Theme theme) { this.theme.set(theme); }

    public ObjectProperty<MultipleChatOpenType> chatOpenTypeProperty() { return chatOpenType; }
    public MultipleChatOpenType getChatOpenType() { return chatOpenType.get(); }
    public void setChatOpenType(MultipleChatOpenType chatOpenType) { this.chatOpenType.set(chatOpenType); }

    public IntegerProperty chatCacheSizeProperty() { return chatCacheSize; }
    public int getChatCacheSize() { return chatCacheSize.get(); }
    public void setChatCacheSize(int chatCacheSize) { this.chatCacheSize.set(chatCacheSize); }
}
