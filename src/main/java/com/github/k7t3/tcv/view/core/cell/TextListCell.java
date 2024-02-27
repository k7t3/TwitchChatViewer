package com.github.k7t3.tcv.view.core.cell;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

/**
 * {@link javafx.scene.control.cell.TextFieldListCell}を置き換えるクラス。
 * 日本語入力するときにIMEが正常に動作しないことがあるため。
 */
public class TextListCell extends ListCell<String> {

    private TextField textField;

    public TextListCell() {
        textField = new TextField();

        textField.setOnAction(e -> {
            commitEdit(textField.getText());
            e.consume();
        });

        textField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                e.consume();
            }
        });
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEditing()) {
            return;
        }

        if (textField == null) {
            textField = new TextField();

            textField.setOnAction(e -> {
                commitEdit(textField.getText());
                e.consume();
            });

            textField.setOnKeyReleased(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    e.consume();
                }
            });
        }

        textField.setText(getItem());

        setText(null);
        setGraphic(textField);

        Platform.runLater(() -> {
            textField.requestFocus();
            textField.selectAll();
        });
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();

        setText(getItem());
        setGraphic(null);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setText(null);
            setGraphic(null);
            return;
        }

        if (isEditing()) {
            if (textField != null) {
                textField.setText(item);
            }
            setText(null);
            setGraphic(textField);
        } else {
            setText(item);
            setGraphic(null);
        }
    }
}
