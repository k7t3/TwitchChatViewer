package com.github.k7t3.tcv.view.prefs.font;

import com.github.k7t3.tcv.app.prefs.FontFamily;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.cell.ComboBoxListCell;

public class FontComboBoxCell extends ComboBoxListCell<FontFamily> {

    private Label label;

    public FontComboBoxCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    public void updateItem(FontFamily item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (label == null) {
            label = new Label();
        }

        label.setText(item.getFamily());
        label.setFont(item.getFont());
        setGraphic(label);
    }
}
