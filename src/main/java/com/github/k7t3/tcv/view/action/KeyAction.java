package com.github.k7t3.tcv.view.action;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public interface KeyAction extends Action {

    boolean accept(KeyEvent e);

    default void install(MenuItem item) {
        item.setOnAction(this);

        if (getCombination() != null) {
            item.acceleratorProperty().bind(combinationProperty());
        }
        item.disableProperty().bind(disableProperty());
    }

    ObjectProperty<KeyCombination> combinationProperty();

    void setCombination(KeyCombination combination);

    KeyCombination getCombination();

    BooleanProperty disableProperty();

    void setDisable(boolean disable);

    boolean isDisable();

}
