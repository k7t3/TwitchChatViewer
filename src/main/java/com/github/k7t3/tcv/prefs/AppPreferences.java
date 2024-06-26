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
     * このアプリケーションで使用する実験的な機能の有効化
     */
    private static final String EXPERIMENTAL = "experimental";

    private final Preferences preferences;

    private final Map<String, WindowPreferences> windowPrefs = new HashMap<>();

    private final GeneralPreferences generalPreferences;

    private BooleanProperty experimental;

    private KeyActionPreferences keyActionPreferences;

    private PlayerPreferences playerPreferences;

    private ChatMessageFilterPreferences messageFilterPreferences;

    private ChatPreferences chatPreferences;

    private AppPreferences() {
        super(Preferences.userNodeForPackage(AppPreferences.class), new HashMap<>());
        defaults.put(EXPERIMENTAL, Boolean.FALSE);

        preferences = Preferences.userNodeForPackage(getClass());
        generalPreferences = new GeneralPreferences(preferences, defaults);

        LOGGER.info("preferences initialized");
    }

    public void clear() {
        try {
            preferences.clear();
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void readFromPreferences() {
        windowPrefs.values().forEach(PreferencesBase::readFromPreferences);

        if (keyActionPreferences != null)
            keyActionPreferences.readFromPreferences();

        if (playerPreferences != null)
            playerPreferences.readFromPreferences();

        if (messageFilterPreferences != null)
            messageFilterPreferences.readFromPreferences();

        if (chatPreferences != null)
            chatPreferences.readFromPreferences();
    }

    @Override
    protected void writeToPreferences() {
        if (messageFilterPreferences != null)
            messageFilterPreferences.writeToPreferences();

        if (chatPreferences != null)
            chatPreferences.writeToPreferences();
    }

    public void importPreferences(Path filePath) {
        try (var input = Files.newInputStream(filePath)) {
            Preferences.importPreferences(input);
            preferences.sync();
        } catch (IOException | InvalidPreferencesFormatException | BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public void exportPreferences(Path exportFilePath) {
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
            writeToPreferences();
            preferences.flush();
            LOGGER.info("save preferences");
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public GeneralPreferences getGeneralPreferences() {
        return generalPreferences;
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
