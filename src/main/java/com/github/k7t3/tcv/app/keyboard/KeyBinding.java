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

package com.github.k7t3.tcv.app.keyboard;

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

    TOGGLE_CHANNEL_LIST(
            "keybind.toggle.channels",
            Resources.getString("command.toggle.channels"),
            KeyCombination.keyCombination("Shortcut+T")
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
