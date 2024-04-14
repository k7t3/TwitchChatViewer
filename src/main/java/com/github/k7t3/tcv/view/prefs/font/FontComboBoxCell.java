package com.github.k7t3.tcv.view.prefs.font;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.cell.ComboBoxListCell;
import javafx.scene.text.Font;

public class FontComboBoxCell extends ComboBoxListCell<Font> {

    private Label label;

    public FontComboBoxCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    public void updateItem(Font item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (label == null) {
            label = new Label();
        }

        label.setText(item.getName());
        label.setFont(item);
        setGraphic(label);
    }
}
