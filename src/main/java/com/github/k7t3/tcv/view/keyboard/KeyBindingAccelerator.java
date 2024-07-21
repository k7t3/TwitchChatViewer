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

package com.github.k7t3.tcv.view.keyboard;

import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCommands;
import javafx.event.EventHandler;
import javafx.scene.input.KeyEvent;

/**
 * {@link KeyEvent}に対して適合するキーバインドを実行するイベントハンドラ
 */
public class KeyBindingAccelerator implements EventHandler<KeyEvent> {

    private final KeyBindingCombinations combinations;
    private final KeyBindingCommands commands;

    public KeyBindingAccelerator(KeyBindingCombinations combinations, KeyBindingCommands commands) {
        this.combinations = combinations;
        this.commands = commands;
    }

    @Override
    public void handle(KeyEvent event) {
        // 適合するキーバインドがあればそのコマンドを実行する
        combinations.getBinding(event).ifPresent(binding -> {
            event.consume();
            commands.executeCommand(binding);
        });
    }
}
