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

package com.github.k7t3.tcv.view.control;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;

public class EditableLabelSkin extends SkinBase<EditableLabel> {

    private final Label label = new Label();

    private final TextField field = new TextField();

    protected EditableLabelSkin(EditableLabel control) {
        super(control);
        init();
    }

    private void init() {
        var control = getSkinnable();

        // ラベルをビューとする
        getChildren().setAll(label);

        label.setMaxWidth(Double.MAX_VALUE);

        // ラベルにテキストをバインドする
        // テキストが空のときは任意のプロンプトをバインド
        label.textProperty().bind(
                control.textProperty()
                        .map(t -> t == null || t.isEmpty() ? control.getPromptText() : t)
        );

        // プロンプトメッセージを表示しているときはMUTEにする
        var emptyCondition = control.textProperty().isEmpty();
        emptyCondition.addListener((ob, o, n) -> {
            if (n)
                label.getStyleClass().add(Styles.TEXT_MUTED);
            else
                label.getStyleClass().remove(Styles.TEXT_MUTED);
        });
        if (emptyCondition.get()) {
            label.getStyleClass().add(Styles.TEXT_MUTED);
        }

        // ラベルをダブルクリックしたらエディタに切り替える
        label.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 2) return;
            e.consume();
            startEdit();
        });

        field.setMaxWidth(Region.USE_COMPUTED_SIZE);

        // フォーカス取得時は全選択とする
        field.focusedProperty().addListener((ob, o, n) -> {
            if (!o && n) {
                // 遅延させる必要がある
                Platform.runLater(field::selectAll);
            }
        });

        // 編集を確定
        field.setOnAction(e -> {
            e.consume();
            commit();
        });

        field.focusedProperty().addListener((ob, o, n) -> {
            // フォーカスを失ったときはキャンセル
            if (getSkinnable().isEditing() && o && !n) {
                cancelEdit();
            }
        });

        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            // Escapeキー押下時はキャンセル
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                cancelEdit();
            }
        });
    }

    private void startEdit() {
        var control = getSkinnable();
        // 編集モード
        control.setEditing(true);

        // 値を反映してビューを切り替え
        field.setText(control.getText());
        getChildren().setAll(field);

        // フォーカスをリクエスト
        field.requestFocus();

        // イベントを発行
        var event = new EditableLabel.EditEvent(control, EditableLabel.editStartEvent(), control.getText());
        control.fireEvent(event);
    }

    private void commit() {
        var control = getSkinnable();
        // 編集モードを切り替え
        control.setEditing(false);
        // 編集内容を登録
        control.setText(field.getText());
        // ビューを戻す
        getChildren().setAll(label);

        // イベントを発行
        var event = new EditableLabel.EditEvent(control, EditableLabel.editCommitEvent(), field.getText());
        control.fireEvent(event);
    }

    private void cancelEdit() {
        var control = getSkinnable();
        // 編集モードを切り替え
        control.setEditing(false);
        // ビューを戻す
        getChildren().setAll(label);

        // イベントを発行
        var event = new EditableLabel.EditEvent(control, EditableLabel.editCancelEvent(), control.getText());
        control.fireEvent(event);
    }
}
