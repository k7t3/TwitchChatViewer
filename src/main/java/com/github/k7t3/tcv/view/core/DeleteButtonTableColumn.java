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

package com.github.k7t3.tcv.view.core;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

public class DeleteButtonTableColumn<T> extends TableCell<T, T> implements AutoCloseable {

    private Button button;

    private final Consumer<T> buttonAction;
    private boolean closed;

    public DeleteButtonTableColumn(Consumer<T> buttonAction) {
        this.buttonAction = buttonAction;
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private Button createButton() {
        var button = new Button(null, new FontIcon(Feather.TRASH));
        button.setAccessibleText(Resources.getString("menu.remove"));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.DANGER);
        button.setOnAction(e -> {
            var item = getItem();
            if (!closed && !isEmpty() && item != null) buttonAction.accept(item);
        });
        return button;
    }

    @Override
    protected void updateItem(T t, boolean b) {
        if (closed) { super.updateItem(null, true); setGraphic(null); return; }
        super.updateItem(t, b);

        if (t == null || b) {
            setGraphic(null);
            return;
        }

        if (button == null) {
            button = createButton();
        }

        setGraphic(button);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (button != null) button.setOnAction(null);
        updateTableRow(null);
        updateItem(null, true);
    }
}
