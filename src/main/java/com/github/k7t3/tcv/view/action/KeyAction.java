package com.github.k7t3.tcv.view.action;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public interface KeyAction extends Action {

    boolean accept(KeyEvent e);

    ObjectProperty<KeyCombination> combinationProperty();

    void setCombination(KeyCombination combination);

    KeyCombination getCombination();

    BooleanProperty disableProperty();

    void setDisable(boolean disable);

    boolean isDisable();

}
