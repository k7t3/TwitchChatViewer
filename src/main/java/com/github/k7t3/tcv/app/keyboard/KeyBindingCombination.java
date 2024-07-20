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
