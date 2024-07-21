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

import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public class KeyCombinationCell extends TableCell<KeyBindingCombination, KeyCombination> {

    private static final String STYLE_CLASS = "key-combination-cell";

    private final BooleanProperty duplicated = new SimpleBooleanProperty(false);

    private TextField textField;
    private KeyCombinationDetector combinationDetector;

    private Tooltip duplicatedTooltip;

    public KeyCombinationCell() {
        getStyleClass().add(STYLE_CLASS);
        setCursor(Cursor.HAND);

        // 編集中のPseudoClass
        JavaFXHelper.registerPseudoClass(this, "editing", editingProperty());

        // キーコンビネーションの重複PseudoClass
        JavaFXHelper.registerPseudoClass(this, "duplicated", duplicated);

        duplicated.addListener((ob, o, n) -> setTooltip(n));
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEditing()) {
            return;
        }

        if (textField == null) {
            var current = getItem();
            combinationDetector = new KeyCombinationDetector(current);
            textField = new TextField();
            textField.textProperty().bind(combinationDetector.keyCombinationProperty().map(KeyCombination::getDisplayText));
            textField.setEditable(false);
            textField.setOnKeyPressed(combinationDetector);
            textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                switch (e.getCode()) {
                    // 編集不可にするとActionが実行できないようなので手動で
                    case ENTER -> {
                        e.consume();
                        if (combinationDetector.isEmpty()) {
                            cancelEdit();
                        } else {
                            var combination = combinationDetector.getKeyCombination();
                            commitEdit(combination);
                        }
                    }
                    case ESCAPE -> {
                        cancelEdit();
                        e.consume();
                    }
                }
            });
        }

        combinationDetector.setKeyCombination(getItem());

        setGraphic(textField);
        textField.requestFocus();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setGraphic(null);
        setText(getItem().getDisplayText());
    }

    @Override
    public void commitEdit(KeyCombination newValue) {
        super.commitEdit(newValue);

        var item = getTableRow().getItem();
        item.setCombination(newValue);
    }

    private void setTooltip(boolean enable) {
        if (enable) {
            if (duplicatedTooltip == null) {
                duplicatedTooltip = new Tooltip(Resources.getString("prefs.keybind.tooltip.duplicated"));
            }
            setTooltip(duplicatedTooltip);
        } else {
            setTooltip(null);
        }
    }

    @Override
    protected void updateItem(KeyCombination item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
            setGraphic(null);
            setTooltip(false);
            duplicated.unbind();
            return;
        }

        var element = getTableRow().getItem();
        duplicated.bind(element.duplicatedProperty());

        if (isEditing()) {
            if (combinationDetector != null) {
                combinationDetector.setKeyCombination(item);
            }
            setText(null);
            setGraphic(textField);
        } else {
            setText(item.getDisplayText());
            setGraphic(null);
        }
    }

}
