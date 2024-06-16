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
