package com.github.k7t3.tcv.view.key;

import com.github.k7t3.tcv.app.key.KeyBindingCombination;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.Objects;

public class KeyCombinationCell extends TableCell<KeyBindingCombination, KeyCombination> {

    private final BooleanProperty duplicated = new SimpleBooleanProperty(false);

    private TextField textField;
    private KeyCombinationDetector combinationDetector;

    public KeyCombinationCell() {
        setCursor(Cursor.HAND);

        // 編集中のPseudoClass
        JavaFXHelper.registerPseudoClass(this, "editing", editingProperty());

        // キーコンビネーションの重複PseudoClass
        JavaFXHelper.registerPseudoClass(this, "duplicated", duplicated);
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
        checkDuplicate();
    }

    private void checkDuplicate() {
        var items = getTableView().getItems();
        items.forEach(item -> item.setDuplicated(false));

        for (var check : items) {
            for (var compare : items) {
                if (check == compare) continue;
                if (Objects.equals(check.getCombination().getDisplayText(),
                        compare.getCombination().getDisplayText())) {
                    check.setDuplicated(true);
                    compare.setDuplicated(true);
                }
            }
        }
    }

    @Override
    protected void updateItem(KeyCombination item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
            setGraphic(null);
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
