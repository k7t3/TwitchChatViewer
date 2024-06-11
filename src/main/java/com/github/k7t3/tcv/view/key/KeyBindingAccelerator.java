package com.github.k7t3.tcv.view.key;

import com.github.k7t3.tcv.app.key.KeyBindingCombinations;
import com.github.k7t3.tcv.app.key.KeyBindingCommands;
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
