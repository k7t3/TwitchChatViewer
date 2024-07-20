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
