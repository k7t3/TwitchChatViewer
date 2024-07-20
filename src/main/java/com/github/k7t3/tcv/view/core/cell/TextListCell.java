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
