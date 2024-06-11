package com.github.k7t3.tcv.app.key;

import com.github.k7t3.tcv.app.core.Resources;
import javafx.scene.input.KeyCombination;

/**
 * 定義済みのキーバインド
 */
public enum KeyBinding {

    OPEN_PREFERENCES(
            "keybind.preferences",
            Resources.getString("command.open.preferences"),
            KeyCombination.keyCombination("Shortcut+,")
    ),

    OPEN_SEARCH_VIEW(
            "keybind.search",
            Resources.getString("command.open.search"),
            KeyCombination.keyCombination("Shortcut+E")
    ),

    OPEN_CLIPS_VIEW(
            "keybind.clips",
            Resources.getString("command.open.clips"),
            KeyCombination.keyCombination("Shortcut+V")
    ),

    OPEN_GROUPS_VIEW(
            "keybind.channel.groups",
            Resources.getString("command.open.groups"),
            KeyCombination.keyCombination("Shortcut+G")
    ),

    QUIT(
            "keybind.quit",
            Resources.getString("command.quit"),
            KeyCombination.keyCombination("Shortcut+Q")
    );

    private final String id;

    private final String displayText;

    private final KeyCombination defaultCombination;

    KeyBinding(String id, String displayText, KeyCombination defaultCombination) {
        this.id = id;
        this.displayText = displayText;
        this.defaultCombination = defaultCombination;
    }

    public String getId() {
        return id;
    }

    public String getDisplayText() {
        return displayText;
    }

    public KeyCombination getDefaultCombination() {
        return defaultCombination;
    }

}
