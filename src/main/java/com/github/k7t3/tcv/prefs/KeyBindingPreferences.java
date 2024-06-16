package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import javafx.scene.input.KeyCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.prefs.Preferences;

public class KeyBindingPreferences extends PreferencesBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindingPreferences.class);

    KeyBindingPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);
    }

    public KeyCombination readCustomCombination(KeyBinding binding) {
        var code = get(binding.getId());
        if (code == null) return KeyCombination.NO_MATCH;
        try {
            return KeyCombination.keyCombination(code);
        } catch (Exception ignored) {
            LOGGER.warn("stored invalid key combination");
            return KeyCombination.NO_MATCH;
        }
    }

    public void storeCombination(KeyBinding binding, KeyCombination combination) {
        // デフォルトキーが指定されたときは既存のカスタムキーバインドを削除
        if (binding.getDefaultCombination().equals(combination)) {
            preferences.remove(binding.getId());
        } else {
            preferences.put(binding.getId(), combination.getName());
        }
    }

    public void clear() {
        for (var binding : KeyBinding.values()) {
            preferences.remove(binding.getId());
        }
    }

    @Override
    protected void readFromPreferences() {
        // no-op
    }

    @Override
    protected void writeToPreferences() {
        // no-op
    }
}
