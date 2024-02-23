package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.view.action.KeyAction;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public class KeyActionPreferences extends PreferencesBase {

    private static final String KEY_COMBINATIONS = "key.combinations";

    private final Map<String, String> combinations = new HashMap<>();

    public KeyActionPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);
        loadKeyActions();
    }

    private void loadKeyActions() {
        var bytes = preferences.getByteArray(KEY_COMBINATIONS, null);
        if (bytes == null)
            return;

        try (var bais = new ByteArrayInputStream(bytes);
             var dis = new DataInputStream(bais)) {

            while (0 < dis.available()) {
                var key = dis.readUTF();
                var value = dis.readUTF();

                combinations.put(key, value);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends KeyAction> Optional<KeyCombination> getKeyCombination(T action) {
        var combination = combinations.get(action.getClass().getName());
        if (combination == null) {
            return Optional.empty();
        }
        return Optional.of(KeyCodeCombination.keyCombination(combination));
    }

    public void storeKeyActions(KeyActionRepository repository) {
        try (var baos = new ByteArrayOutputStream();
             var dos = new DataOutputStream(baos)) {

            for (var action : repository.getActions()) {
                dos.writeUTF(action.getClass().getName());
                dos.writeUTF(action.getCombination().getName());
            }

            dos.flush();

            var bytes = baos.toByteArray();

            preferences.putByteArray(KEY_COMBINATIONS, bytes);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
