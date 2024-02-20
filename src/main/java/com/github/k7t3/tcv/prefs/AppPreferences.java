package com.github.k7t3.tcv.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.view.core.StageBounds;
import com.github.k7t3.tcv.view.core.ThemeManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class AppPreferences {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);

    private static final String THEME = "theme";

    private static final String CHAT_FONT_FAMILY = "chat.font.family";
    private static final String CHAT_SHOW_USERNAME = "chat.show.username";
    private static final String CHAT_SHOW_BADGES = "chat.show.badges";

    private static final String WINDOW_X = "window.x";
    private static final String WINDOW_Y = "window.y";
    private static final String WINDOW_WIDTH = "window.width";
    private static final String WINDOW_HEIGHT = "window.height";
    private static final String WINDOW_MAXIMIZED = "window.maximized";

    private final Preferences preferences;

    private final Map<String, Object> defaults = new HashMap<>();

    private ObjectProperty<Font> font;

    private BooleanProperty showUserName;

    private BooleanProperty showBadges;

    private AppPreferences() {
        defaults.put(THEME, ThemeManager.DEFAULT_THEME.getName());
        defaults.put(CHAT_FONT_FAMILY, Font.getDefault().getFamily());
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

    public void setStageBounds(StageBounds bounds) {
        preferences.putDouble(WINDOW_X, bounds.x());
        preferences.putDouble(WINDOW_Y, bounds.y());
        preferences.putDouble(WINDOW_WIDTH, bounds.width());
        preferences.putDouble(WINDOW_HEIGHT, bounds.height());
        preferences.putBoolean(WINDOW_MAXIMIZED, bounds.maximized());
    }

    public StageBounds getStageBounds() {
        var x = getDouble(WINDOW_X);
        var y = getDouble(WINDOW_Y);
        var width = getDouble(WINDOW_WIDTH);
        var height = getDouble(WINDOW_HEIGHT);
        var maximized = getBoolean(WINDOW_MAXIMIZED);
        return new StageBounds(x, y, width, height, maximized);
    }

    public void save() {
        try {
            preferences.flush();
            LOGGER.info("save preferences");
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private String get(String key) {
        return preferences.get(key, (String) defaults.get(key));
    }

    private double getDouble(String key) {
        return preferences.getDouble(key, (Double) defaults.get(key));
    }

    private boolean getBoolean(String key) {
        return preferences.getBoolean(key, (Boolean) defaults.get(key));
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<Font> fontProperty() {
        if (font == null) {
            font = new SimpleObjectProperty<>(Font.font(get(CHAT_FONT_FAMILY)));
            font.addListener((ob, o, n) -> preferences.put(CHAT_FONT_FAMILY, n.getFamily()));
        }
        return font;
    }
    public Font getFont() { return fontProperty().get(); }
    public void setFont(Font font) { this.fontProperty().set(font); }

    public BooleanProperty showUserNameProperty() {
        if (showUserName == null) {
            showUserName = new SimpleBooleanProperty(getBoolean(CHAT_SHOW_USERNAME));
            showUserName.addListener((ob, o, n) -> preferences.putBoolean(CHAT_SHOW_USERNAME, n));
        }
        return showUserName;
    }
    public boolean isShowUserName() { return showUserNameProperty().get(); }
    public void setShowUserName(boolean showUserName) { this.showUserNameProperty().set(showUserName); }

    public BooleanProperty showBadgesProperty() {
        if (showBadges == null) {
            showBadges = new SimpleBooleanProperty(getBoolean(CHAT_SHOW_BADGES));
            showBadges.addListener((ob, o, n) -> preferences.putBoolean(CHAT_SHOW_BADGES, n));
        }
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
