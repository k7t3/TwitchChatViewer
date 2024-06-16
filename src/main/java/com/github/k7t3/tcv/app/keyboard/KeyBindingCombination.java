package com.github.k7t3.tcv.app.keyboard;

import javafx.beans.property.*;
import javafx.scene.input.KeyCombination;

/**
 * キーバインドとそのコンビ―ネーションのプロパティを持つクラス
 */
public class KeyBindingCombination {

    private final ReadOnlyObjectWrapper<KeyBinding> binding;
    private final ObjectProperty<KeyCombination> combination;

    // キーコンビネーションが重複しているか
    private BooleanProperty duplicated;

    public KeyBindingCombination(KeyBinding binding, KeyCombination combination) {
        this.binding = new ReadOnlyObjectWrapper<>(binding);
        this.combination = new SimpleObjectProperty<>(combination);
    }

    @Override
    public String toString() {
        return "KeyBindingCombination{" +
                "binding=" + getBinding() +
                ", combination=" + getCombination() +
                ", duplicated=" + isDuplicated() +
                '}';
    }

    public ReadOnlyObjectProperty<KeyBinding> bindingProperty() { return binding.getReadOnlyProperty(); }
    public KeyBinding getBinding() { return binding.get(); }

    public ObjectProperty<KeyCombination> combinationProperty() { return combination; }
    public KeyCombination getCombination() { return combination.get(); }
    public void setCombination(KeyCombination combination) { this.combination.set(combination); }

    public void resetKeyCombination() {
        var binding = getBinding();
        setCombination(binding.getDefaultCombination());
    }

    /**
     * キーコンビネーションが重複しているかを表すプロパティを返す
     * @return キーコンビネーションが重複しているかを表すプロパティ
     */
    public BooleanProperty duplicatedProperty() {
        if (duplicated == null) duplicated = new SimpleBooleanProperty(false);
        return duplicated;
    }

    /**
     * @see #duplicatedProperty()
     */
    public boolean isDuplicated() { return duplicated != null && duplicated.get(); }

    /**
     * @see #duplicatedProperty()
     */
    public void setDuplicated(boolean duplicated) { duplicatedProperty().set(duplicated); }
}
