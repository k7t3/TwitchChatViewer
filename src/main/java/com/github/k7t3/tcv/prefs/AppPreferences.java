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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;

public class AppPreferences extends PreferencesBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);

    private final Preferences preferences;

    private final GeneralPreferences generalPreferences;

    private ChatPreferences chatPreferences;

    private KeyBindingPreferences keyBindingPreferences;

    private StatePreferences statePreferences;

    private AppPreferences() {
        super(Preferences.userNodeForPackage(AppPreferences.class), new HashMap<>());

        preferences = Preferences.userRoot().node("/com/github/k7t3/tcv");
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
        if (chatPreferences != null)
            chatPreferences.readFromPreferences();
    }

    @Override
    protected void writeToPreferences() {
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

    public ChatPreferences getChatPreferences() {
        if (chatPreferences == null) {
            chatPreferences = new ChatPreferences(preferences, defaults);
        }
        return chatPreferences;
    }

    public KeyBindingPreferences getKeyBindingPreferences() {
        if (keyBindingPreferences == null) {
            keyBindingPreferences = new KeyBindingPreferences(preferences, defaults);
        }
        return keyBindingPreferences;
    }

    public StatePreferences getStatePreferences() {
        if (statePreferences == null)
            statePreferences = new StatePreferences(preferences, defaults);
        return statePreferences;
    }

    // ******************** PROPERTIES ********************

    public static AppPreferences getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final AppPreferences INSTANCE = new AppPreferences();
    }

}
