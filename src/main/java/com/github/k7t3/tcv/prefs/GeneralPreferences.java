package com.github.k7t3.tcv.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.view.core.ThemeManager;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;

import java.util.Map;
import java.util.prefs.Preferences;

public class GeneralPreferences extends PreferencesBase {

    private static final int DEFAULT_CHAT_CACHE_SIZE = 256;

    /**
     * このアプリケーションに適用されるAtlantaFXのテーマの名称
     */
    private static final String THEME = "theme";

    /**
     * チャンネルを複数選択して開くときの動作
     */
    private static final String MULTIPLE_OPEN_TYPE = "multiple.open.type";

    /**
     * チャンネルごとにチャットをキャッシュする数
     */
    private static final String CHAT_CACHE_SIZE = "chat.cache.size";

    private ObjectProperty<MultipleChatOpenType> multipleOpenType;

    private IntegerProperty chatCacheSize;

    GeneralPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(THEME, ThemeManager.DEFAULT_THEME.getName());
        defaults.put(MULTIPLE_OPEN_TYPE, MultipleChatOpenType.MERGED.name());
        defaults.put(CHAT_CACHE_SIZE, DEFAULT_CHAT_CACHE_SIZE);
    }

    public void setTheme(Theme theme) {
        preferences.put(THEME, theme.getName());
    }

    public Theme getTheme() {
        var name = preferences.get(THEME, (String) defaults.get(THEME));
        return ThemeManager.getInstance().findTheme(name).orElseThrow();
    }

    @Override
    protected void readFromPreferences() {
        var themeName = get(THEME);
        if (themeName != null) {
            ThemeManager.getInstance().findTheme(themeName).ifPresent(this::setTheme);
        }

        try {
            var type = MultipleChatOpenType.valueOf(get(MULTIPLE_OPEN_TYPE));
            if (getMultipleOpenType() != type) {
                setMultipleOpenType(type);
            }
        } catch (IllegalArgumentException ignored) {
        }

        var chatCacheSize = getInt(CHAT_CACHE_SIZE);
        if (chatCacheSize != getChatCacheSize()) {
            setChatCacheSize(chatCacheSize);
        }
    }

    @Override
    protected void writeToPreferences() {
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<MultipleChatOpenType> multipleOpenTypeProperty() {
        if (multipleOpenType == null) {
            multipleOpenType = createObjectProperty(
                    MULTIPLE_OPEN_TYPE,
                    MultipleChatOpenType::valueOf,
                    Enum::name);
        }
        return multipleOpenType;
    }
    public MultipleChatOpenType getMultipleOpenType() { return multipleOpenTypeProperty().get(); }
    public void setMultipleOpenType(MultipleChatOpenType multipleOpenType) { multipleOpenTypeProperty().set(multipleOpenType); }

    public IntegerProperty chatCacheSizeProperty() {
        if (chatCacheSize == null) chatCacheSize = createIntegerProperty(CHAT_CACHE_SIZE);
        return chatCacheSize;
    }
    public int getChatCacheSize() { return chatCacheSizeProperty().get(); }
    public void setChatCacheSize(int chatCacheSize) { chatCacheSizeProperty().set(chatCacheSize); }

}
