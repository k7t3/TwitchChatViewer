package com.github.k7t3.tcv.view.keyboard;

import com.github.k7t3.tcv.app.core.OS;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;

/**
 * キーイベントに含まれる{@link KeyCombination}を検出するイベントハンドラ
 * <p>
 *     {@link #keyCombinationProperty()}は直近で検出できたKeyCombinationが扱われる。
 *     初期値は{@link KeyCombination#NO_MATCH}。
 * </p>
 * <p>
 *     {@link #clear()}をコールすると初期値が設定される。
 * </p>
 */
public class KeyCombinationDetector implements EventHandler<KeyEvent> {

    private final ObjectProperty<KeyCombination> keyCombination = new SimpleObjectProperty<>(KeyCombination.NO_MATCH);

    public KeyCombinationDetector() {
    }

    public KeyCombinationDetector(KeyCombination initialValue) {
        setKeyCombination(initialValue);
    }

    @Override
    public void handle(KeyEvent event) {
        // イベントを消費
        event.consume();

        if (event.getCode() == KeyCode.UNDEFINED) return;
        if (event.getCode().isModifierKey()) return;

        var modifiers = new ArrayList<KeyCombination.Modifier>();
        if (event.isShiftDown()) modifiers.add(KeyCombination.SHIFT_DOWN);
        if (event.isAltDown()) modifiers.add(KeyCombination.ALT_DOWN);
        if (event.isShortcutDown()) modifiers.add(KeyCombination.SHORTCUT_DOWN);

        // Shortcutキーがプラットフォーム固有のショートカットキーを吸収するため、
        // それらの入力は個別に回収しない。
        if (OS.isMac()) {
            // ControlキーはOS Xのときのみ回収する
            if (event.isControlDown()) modifiers.add(KeyCombination.CONTROL_DOWN);
        } else {
            // MetaキーはOS X以外のときのみ回収する
            if (event.isMetaDown()) modifiers.add(KeyCombination.META_DOWN);
        }

        KeyCombination combination;
        if (modifiers.isEmpty()) {
            return;
        } else {
            combination = new KeyCodeCombination(
                    event.getCode(),
                    modifiers.toArray(new KeyCombination.Modifier[0])
            );
        }
        keyCombination.set(combination);
    }

    public void clear() {
        keyCombination.set(KeyCombination.NO_MATCH);
    }

    public boolean isEmpty() {
        return getKeyCombination() == null || KeyCombination.NO_MATCH.equals(getKeyCombination());
    }

    public ObjectProperty<KeyCombination> keyCombinationProperty() { return keyCombination; }
    public KeyCombination getKeyCombination() { return keyCombinationProperty().get(); }
    public void setKeyCombination(KeyCombination combination) {
        keyCombination.set(combination == null ? KeyCombination.NO_MATCH : combination);
    }
}
