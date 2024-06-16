package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.keyboard.KeyBindingCombination;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.prefs.KeyBindingPreferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.stream.Collectors;

public class KeyBindingPreferencesViewModel implements PreferencesViewModelBase {

    private final KeyBindingPreferences preferences;

    private final KeyBindingCombinations combinations;

    private final ObservableList<KeyBindingCombination> combinationList;

    public KeyBindingPreferencesViewModel(KeyBindingPreferences preferences, KeyBindingCombinations combinations) {
        this.preferences = preferences;
        this.combinations = combinations;
        this.combinationList = FXCollections.observableList(combinations.getAllBindings());
        combinationList.forEach(binding -> binding.combinationProperty().addListener((ob, o, n) -> checkDuplicate()));
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
        return combinationList.stream().noneMatch(KeyBindingCombination::isDuplicated);
    }

    @Override
    public void sync() {
        var current = combinations.getAllBindings();
        var currentValues = current.stream().collect(
                Collectors.toMap(
                        KeyBindingCombination::getBinding,
                        KeyBindingCombination::getCombination
                )
        );
        for (var entry : combinationList) {
            var currentCombination = currentValues.get(entry.getBinding());
            // 変更されている場合更新
            if (!currentCombination.equals(entry.getCombination())) {
                preferences.storeCombination(entry.getBinding(), entry.getCombination());
                combinations.updateCombination(entry.getBinding(), entry.getCombination());
            }
        }
    }

}
