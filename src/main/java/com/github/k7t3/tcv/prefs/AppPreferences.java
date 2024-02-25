package com.github.k7t3.tcv.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.view.core.ThemeManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class AppPreferences extends PreferencesBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);

    /**
     * このアプリケーションに適用されるAtlantaFXのテーマの名称
     */
    private static final String THEME = "theme";

    /**
     * このアプリケーションで使用する実験的な機能の有効化
     */
    private static final String EXPERIMENTAL = "experimental";

    /**
     * チャットビューで適用されるフォントファミリ
     */
    private static final String CHAT_FONT_FAMILY = "chat.font.family";
    /**
     * チャットビューでユーザー名を表示するか
     */
    private static final String CHAT_SHOW_USERNAME = "chat.show.username";
    /**
     * チャットビューでユーザーのバッジを表示するか
     */
    private static final String CHAT_SHOW_BADGES = "chat.show.badges";

    // ウインドウの矩形情報
    private static final String WINDOW_X = "window.x";
    private static final String WINDOW_Y = "window.y";
    private static final String WINDOW_WIDTH = "window.width";
    private static final String WINDOW_HEIGHT = "window.height";
    private static final String WINDOW_MAXIMIZED = "window.maximized";

    private final Preferences preferences;

    private final Map<String, WindowPreferences> windowPrefs = new HashMap<>();

    private BooleanProperty experimental;

    private ObjectProperty<ChatFont> font;

    private BooleanProperty showUserName;

    private BooleanProperty showBadges;

    private KeyActionPreferences keyActionPreferences;

    private PlayerPreferences playerPreferences;

    private AppPreferences() {
        super(Preferences.userNodeForPackage(AppPreferences.class), new HashMap<>());
        defaults.put(THEME, ThemeManager.DEFAULT_THEME.getName());
        defaults.put(EXPERIMENTAL, Boolean.FALSE);
        defaults.put(CHAT_FONT_FAMILY, ChatFont.DEFAULT.getFamily());
        defaults.put(CHAT_SHOW_USERNAME, Boolean.TRUE);
        defaults.put(CHAT_SHOW_BADGES, Boolean.TRUE);

        defaults.put(WINDOW_X, Double.NaN);
        defaults.put(WINDOW_Y, Double.NaN);
        defaults.put(WINDOW_WIDTH, 1024d);
        defaults.put(WINDOW_HEIGHT, 768d);
        defaults.put(WINDOW_MAXIMIZED, Boolean.FALSE);

        preferences = Preferences.userNodeForPackage(getClass());

        LOGGER.info("preferences initialized");
    }

    public void setTheme(Theme theme) {
        preferences.put(THEME, theme.getName());
    }

    public Theme getTheme() {
        var name = preferences.get(THEME, (String) defaults.get(THEME));
        return ThemeManager.getInstance().findTheme(name).orElseThrow();
    }

    public WindowPreferences getWindowPreferences(String windowName) {
        return windowPrefs.computeIfAbsent(windowName, k -> new WindowPreferences(preferences, defaults, k));
    }

    // FIXME CredentialStorageにどうやって渡すか
    public Preferences getPreferences() {
        return preferences;
    }

    public void save() {
        try {
            preferences.flush();
            LOGGER.info("save preferences");
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public KeyActionPreferences getKeyActionPreferences() {
        if (keyActionPreferences == null) {
            keyActionPreferences = new KeyActionPreferences(preferences, defaults);
        }
        return keyActionPreferences;
    }

    public PlayerPreferences getPlayerPreferences() {
        if (playerPreferences == null) {
            playerPreferences = new PlayerPreferences(preferences, defaults);
        }
        return playerPreferences;
    }
// ******************** PROPERTIES ********************

    public BooleanProperty experimentalProperty() {
        if (experimental == null) experimental = createBooleanProperty(EXPERIMENTAL);
        return experimental;
    }
    public boolean isExperimental() { return experimentalProperty().get(); }
    public void setExperimental(boolean experimental) { experimentalProperty().set(experimental); }

    public ObjectProperty<ChatFont> fontProperty() {
        if (font == null) {
            var family = get(CHAT_FONT_FAMILY);
            font = new SimpleObjectProperty<>(new ChatFont(family));
            font.addListener((ob, o, n) -> preferences.put(CHAT_FONT_FAMILY, n.getFamily()));
        }
        return font;
    }
    public ChatFont getFont() { return fontProperty().get(); }
    public void setFont(ChatFont font) { this.fontProperty().set(font); }

    public BooleanProperty showUserNameProperty() {
        if (showUserName == null) showUserName = createBooleanProperty(CHAT_SHOW_USERNAME);
        return showUserName;
    }
    public boolean isShowUserName() { return showUserNameProperty().get(); }
    public void setShowUserName(boolean showUserName) { this.showUserNameProperty().set(showUserName); }

    public BooleanProperty showBadgesProperty() {
        if (showBadges == null) showBadges = createBooleanProperty(CHAT_SHOW_BADGES);
        return showBadges;
    }
    public boolean isShowBadges() { return showBadgesProperty().get(); }
    public void setShowBadges(boolean showBadges) { this.showBadgesProperty().set(showBadges); }

    public static AppPreferences getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final AppPreferences INSTANCE = new AppPreferences();
    }

}
