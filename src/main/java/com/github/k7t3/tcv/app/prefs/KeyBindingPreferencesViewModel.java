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

package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.prefs.KeyBindingPreferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

public class KeyBindingPreferencesViewModel implements PreferencesViewModelBase {

    private final ObservableList<KeyBindingCombination> combinationList;

    public KeyBindingPreferencesViewModel(KeyBindingCombinations combinations) {
        this.combinationList = FXCollections.observableList(combinations.getAllBindings());
        combinationList.forEach(binding -> binding.combinationProperty().addListener((ob, o, n) -> checkDuplicate()));
        checkDuplicate();
    }

    public ObservableList<KeyBindingCombination> getCombinationList() {
        return combinationList;
    }

    public void resetAll() {
        for (var item : combinationList) {
            item.resetKeyCombination();
        }
    }

    private void checkDuplicate() {
        var items = combinationList;
        items.forEach(item -> item.setDuplicated(false));

        for (var check : items) {
            for (var compare : items) {
                if (check == compare) continue;
                if (check.getCombination() == null || compare.getCombination() == null) continue;
                if (Objects.equals(check.getCombination().getDisplayText(),
                        compare.getCombination().getDisplayText())) {
                    check.setDuplicated(true);
                    compare.setDuplicated(true);
                }
            }
        }
    }

    @Override
    public boolean canSync() {
        return combinationList.stream().allMatch(item -> item.getCombination() != null && !item.isDuplicated());
    }

    @Override
    public void prepare(SettingsDraft.Builder target) {
        if (!canSync()) throw new IllegalStateException("key bindings are incomplete or duplicated");
        var values = new java.util.HashMap<String, java.util.Optional<String>>();
        for (var entry : combinationList) {
            values.put(entry.getBinding().getId(), KeyBindingPreferences.editorValue(entry.getBinding(), entry.getCombination()));
        }
        target.values(values);
    }

}
