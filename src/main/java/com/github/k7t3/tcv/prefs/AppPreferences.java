package com.github.k7t3.tcv.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.view.core.ThemeManager;
import javafx.beans.property.BooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
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

    private final Preferences preferences;

    private final Map<String, WindowPreferences> windowPrefs = new HashMap<>();

    private BooleanProperty experimental;

    private KeyActionPreferences keyActionPreferences;

    private PlayerPreferences playerPreferences;

    private ChatMessageFilterPreferences messageFilterPreferences;

    private ChatPreferences chatPreferences;

    private AppPreferences() {
        super(Preferences.userNodeForPackage(AppPreferences.class), new HashMap<>());
        defaults.put(THEME, ThemeManager.DEFAULT_THEME.getName());
        defaults.put(EXPERIMENTAL, Boolean.FALSE);

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

    public void clear() {
        try {
            preferences.clear();
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onImported() {
        windowPrefs.values().forEach(PreferencesBase::onImported);

        if (keyActionPreferences != null)
            keyActionPreferences.onImported();

        if (playerPreferences != null)
            playerPreferences.onImported();

        if (messageFilterPreferences != null)
            messageFilterPreferences.onImported();

        if (chatPreferences != null)
            chatPreferences.onImported();
    }

    public void importPrefs(Path filePath) {
        try (var input = Files.newInputStream(filePath)) {
            Preferences.importPreferences(input);
            preferences.sync();
        } catch (IOException | InvalidPreferencesFormatException | BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public void exportPrefs(Path exportFilePath) {
        try (var output = Files.newOutputStream(exportFilePath)) {
            preferences.exportNode(output);
        } catch (IOException | BackingStoreException e) {
            throw new RuntimeException(e);
        }
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

    public ChatMessageFilterPreferences getMessageFilterPreferences() {
        if (messageFilterPreferences == null) {
            messageFilterPreferences = new ChatMessageFilterPreferences(preferences, defaults);
        }
        return messageFilterPreferences;
    }

    public ChatPreferences getChatPreferences() {
        if (chatPreferences == null) {
            chatPreferences = new ChatPreferences(preferences, defaults);
        }
        return chatPreferences;
    }

    // ******************** PROPERTIES ********************

    public BooleanProperty experimentalProperty() {
        if (experimental == null) experimental = createBooleanProperty(EXPERIMENTAL);
        return experimental;
    }
    public boolean isExperimental() { return experimentalProperty().get(); }
    public void setExperimental(boolean experimental) { experimentalProperty().set(experimental); }

    public static AppPreferences getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final AppPreferences INSTANCE = new AppPreferences();
    }

}
