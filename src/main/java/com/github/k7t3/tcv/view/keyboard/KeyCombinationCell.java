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
import com.github.k7t3.tcv.view.core.ViewResources;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.event.EventHandler;

public class KeyCombinationCell extends TableCell<KeyBindingCombination, KeyCombination> implements AutoCloseable {

    private static final String STYLE_CLASS = "key-combination-cell";

    private final BooleanProperty duplicated = new SimpleBooleanProperty(false);
    private final ViewResources owned = new ViewResources();

    private TextField textField;
    private KeyCombinationDetector combinationDetector;

    private Tooltip duplicatedTooltip;
    private EventHandler<KeyEvent> editorFilter;
    private KeyBindingCombination editingItem;

    public KeyCombinationCell() {
        getStyleClass().add(STYLE_CLASS);
        setCursor(Cursor.HAND);

        // 編集中のPseudoClass
        owned.add(JavaFXHelper.registerPseudoClass(this, "editing", editingProperty())::unsubscribe);

        // キーコンビネーションの重複PseudoClass
        owned.add(JavaFXHelper.registerPseudoClass(this, "duplicated", duplicated)::unsubscribe);

        owned.add(duplicated.subscribe(this::setTooltip)::unsubscribe);
        var rowDuplicated = tableRowProperty().flatMap(TableRow::itemProperty)
                .flatMap(KeyBindingCombination::duplicatedProperty).orElse(false);
        duplicated.bind(Bindings.createBooleanBinding(
                () -> !isEmpty() && rowDuplicated.getValue(), emptyProperty(), rowDuplicated));
        owned.add(duplicated::unbind);
        owned.add(tableRowProperty().flatMap(TableRow::itemProperty).subscribe(item -> {
            if (isEditing() && editingItem != item) cancelEdit();
        })::unsubscribe);
    }

    @Override
    public void startEdit() {
        if (owned.isClosed() || isEmpty() || getItem() == null || getTableRow() == null || getTableRow().getItem() == null) return;
        super.startEdit();
        if (!isEditing()) {
            return;
        }
        editingItem = getTableRow().getItem();

        if (textField == null) {
            var current = getItem();
            combinationDetector = new KeyCombinationDetector(current);
            textField = new TextField();
            textField.textProperty().bind(combinationDetector.keyCombinationProperty().map(KeyCombination::getDisplayText));
            textField.setEditable(false);
            textField.setOnKeyPressed(e -> {
                if (!owned.isClosed() && isEditing()) combinationDetector.handle(e);
            });
            editorFilter = e -> {
                if (owned.isClosed() || !isEditing()) return;
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
            };
            textField.addEventFilter(KeyEvent.KEY_PRESSED, editorFilter);
        }

        combinationDetector.setKeyCombination(getItem());

        setGraphic(textField);
        setText(null);
        textField.requestFocus();
    }

    @Override
    public void cancelEdit() {
        editingItem = null;
        super.cancelEdit();
        setGraphic(null);
        setText(owned.isClosed() || getItem() == null || isEmpty() ? null : getItem().getDisplayText());
    }

    @Override
    public void commitEdit(KeyCombination newValue) {
        if (owned.isClosed() || !isEditing() || getTableRow() == null || getTableRow().getItem() != editingItem) return;
        super.commitEdit(newValue);
        editingItem = null;
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
        if (owned.isClosed()) {
            super.updateItem(null, true);
            setText(null); setGraphic(null);
            return;
        }
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
            setGraphic(null);
            setTooltip(false);
            return;
        }

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

    @Override public void close() {
        if (owned.isClosed()) return;
        owned.close();
        cancelEdit();
        duplicated.set(false);
        JavaFXHelper.updatePseudoClass(this, "editing", false);
        JavaFXHelper.updatePseudoClass(this, "duplicated", false);
        setTooltip(null);
        if (textField != null) {
            textField.textProperty().unbind();
            textField.setText(null);
            textField.setOnKeyPressed(null);
            textField.removeEventFilter(KeyEvent.KEY_PRESSED, editorFilter);
            combinationDetector.clear();
        }
        updateTableRow(null);
        updateItem(null, true);
    }

}
