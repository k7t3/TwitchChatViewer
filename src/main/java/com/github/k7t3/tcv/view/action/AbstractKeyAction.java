package com.github.k7t3.tcv.view.action;

import com.github.k7t3.tcv.prefs.AppPreferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public abstract class AbstractKeyAction implements KeyAction {

    private final ObjectProperty<KeyCombination> combination;

    private BooleanProperty disable;

    public AbstractKeyAction(KeyCombination defaultCombination) {
        combination = new SimpleObjectProperty<>();

        var prefs = AppPreferences.getInstance();
        var keyPrefs = prefs.getKeyActionPreferences();
        var op = keyPrefs.getKeyCombination(this);
        if (op.isPresent())
            setCombination(op.get());
        else
            setCombination(defaultCombination);
    }

    @Override
    public boolean accept(KeyEvent e) {
        return getCombination().match(e);
    }

    @Override
    public ObjectProperty<KeyCombination> combinationProperty() { return combination; }

    @Override
    public KeyCombination getCombination() { return combination.get(); }

    @Override
    public void setCombination(KeyCombination combination) { this.combination.set(combination); }

    @Override
    public BooleanProperty disableProperty() {
        if (disable == null) disable = new SimpleBooleanProperty(false);
        return disable;
    }
    @Override
    public void setDisable(boolean disable) { disableProperty().set(disable); }
    @Override
    public boolean isDisable() { return disable != null && disable.get(); }

}
