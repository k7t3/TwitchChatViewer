package com.github.k7t3.tcv.app.key;

import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.*;

/**
 * キーバインドと対応するキーコンビネーションを管理するリポジトリ
 */
public class KeyBindingCombinations {

    private static final Comparator<KeyBinding> COMPARATOR =
            Comparator.comparing(KeyBinding::getDisplayText);

    private final TreeMap<KeyBinding, KeyCombination> defaults = new TreeMap<>(COMPARATOR);
    private final TreeMap<KeyBinding, KeyCombination> custom = new TreeMap<>(COMPARATOR);

    public KeyBindingCombinations() {
        // 定義済みのキーバインドを初期値として登録する
        for (var binding : KeyBinding.values())
            defaults.put(binding, binding.getDefaultCombination());
    }

    /**
     * キーバインドに対応するコンビネーションを更新する。
     * <p>
     *     更新するキーコンビネーションは
     *     他のキーバインドのものと重複しないようにすること。
     * </p>
     * @param binding キーバインド
     * @param combination 新しい値
     */
    public void updateCombination(KeyBinding binding, KeyCombination combination) {
        var defaultCombination = binding.getDefaultCombination();
        if (defaultCombination.equals(combination)) {
            custom.remove(binding);
            return;
        }
        custom.put(binding, combination);
    }

    /**
     * 全てのキーバインド情報を取得する
     * @return キーバインド情報
     */
    public List<KeyBindingCombination> getAllBindings() {
        var bindings = new ArrayList<KeyBindingCombination>();
        defaults.forEach((binding, combination) -> {
            var updated = custom.get(binding);
            bindings.add(new KeyBindingCombination(binding, updated != null ? updated : combination));
        });
        return bindings;
    }

    /**
     * キーイベントに適合するキーバインドを取得する
     * @param event キーイベント
     * @return キーバインド
     */
    public Optional<KeyBinding> getBinding(KeyEvent event) {
        var o = getBinding(custom, event);
        if (o.isPresent()) {
            return o;
        }
        return getBinding(defaults, event);
    }

    private Optional<KeyBinding> getBinding(Map<KeyBinding, KeyCombination> bindings, KeyEvent event) {
        return bindings.entrySet()
                .stream()
                .filter(e -> e.getValue().match(event))
                .findFirst()
                .map(Map.Entry::getKey);
    }

    /**
     * キーバインドをデフォルトにリセットする
     * <p>
     *     デフォルトに戻した結果、他のキーコンビネーションと
     *     競合するようであればリセットされず、falseが返される。
     * </p>
     * @param binding キーバインド
     * @return リセットに成功した場合はtrue
     */
    public boolean reset(KeyBinding binding) {
        var defaultCombination = binding.getDefaultCombination();
        if (custom.containsValue(defaultCombination)) {
            return false;
        }
        custom.remove(binding);
        return true;
    }

    /**
     * 更新されたすべてのキーバインドをデフォルトにリセットする
     */
    public void reset() {
        custom.clear();
    }

}
