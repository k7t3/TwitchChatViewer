package com.github.k7t3.tcv.view.prefs.font;

import com.github.k7t3.tcv.prefs.ChatFont;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.cell.ComboBoxListCell;

public class FontComboBoxCell extends ComboBoxListCell<ChatFont> {

    private Label label;

    public FontComboBoxCell() {
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    public void updateItem(ChatFont item, boolean empty) {
        super.updateItem(item, empty);

        if (item == null || empty) {
            setGraphic(null);
            return;
        }

        if (label == null) {
            label = new Label();
        }

        var font = item.getFont();

        label.setText(font.getName());
        label.setFont(font);
        setGraphic(label);
    }
}
