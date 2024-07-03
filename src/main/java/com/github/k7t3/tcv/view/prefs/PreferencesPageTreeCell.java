package com.github.k7t3.tcv.view.prefs;

import javafx.scene.control.TreeCell;

public class PreferencesPageTreeCell extends TreeCell<PreferencesPage<?>> {

    @Override
    protected void updateItem(PreferencesPage<?> page, boolean b) {
        super.updateItem(page, b);

        if (page == null || b) {
            setGraphic(null);
            setText(null);
        } else {
            setGraphic(page.getGraphic());
            setText(page.getName());
        }
    }
}
