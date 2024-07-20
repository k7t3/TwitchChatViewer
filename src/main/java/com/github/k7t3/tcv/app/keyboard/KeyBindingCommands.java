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

import de.saxsys.mvvmfx.utils.commands.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * キーバインド({@link KeyBinding})に対応するコマンドを管理するクラス。
 */
public class KeyBindingCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindingCommands.class);

    private final Map<KeyBinding, Command> commands = new HashMap<>();

    /**
     * キーバインドに対応するコマンドを更新する
     * @param binding キーバインド
     * @param command 対応するコマンド
     */
    public void updateCommand(KeyBinding binding, Command command) {
        commands.put(binding, command);
    }

    /**
     * キーバインドに対応するコマンドを実行する
     * <p>
     *     対応するコマンドが登録されていないときは何もしない。
     * </p>
     * @param binding キーバインド
     */
    public void executeCommand(KeyBinding binding) {
        var command = commands.get(binding);
        if (command == null) {
            LOGGER.warn("not found {} command.", binding);
            return;
        }
        if (command.isNotExecutable()) return;
        command.execute();
    }

}
