package com.github.k7t3.tcv.view.control;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

        // ラベルは常に最新の値を表示する
        label.textProperty().bind(control.textProperty());

        // ラベルをクリックしたらエディタに切り替える
        label.setOnMousePressed(e -> {
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
