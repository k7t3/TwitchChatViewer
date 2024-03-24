package com.github.k7t3.tcv.view.core;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;

public abstract class ButtonTableColumnCell<T> extends TableCell<T, T> {

    private Button button;

    public ButtonTableColumnCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    protected abstract Button createButton();

    @Override
    protected void updateItem(T t, boolean b) {
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
}
