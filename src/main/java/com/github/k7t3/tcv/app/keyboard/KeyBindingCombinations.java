package com.github.k7t3.tcv.app.keyboard;

import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * キーバインドと対応するキーコンビネーションを管理するリポジトリ
 */
public class KeyBindingCombinations {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindingCombinations.class);

    private static final Comparator<KeyBinding> COMPARATOR =
            Comparator.comparing(KeyBinding::getDisplayText);

    private final TreeMap<KeyBinding, KeyCombination> custom = new TreeMap<>(COMPARATOR);

    public KeyBindingCombinations() {
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
            LOGGER.info("{} binding reset", binding);
            custom.remove(binding);
            return;
        }
        LOGGER.info("{} binding updated to {}", binding, combination);
        custom.put(binding, combination);
    }

    /**
     * 全てのキーバインド情報を取得する
     * @return キーバインド情報
     */
    public List<KeyBindingCombination> getAllBindings() {
        var bindings = new ArrayList<KeyBindingCombination>();
        for (var binding : KeyBinding.values()) {
            var combination = binding.getDefaultCombination();
            var updated = custom.get(binding);
            bindings.add(new KeyBindingCombination(binding, updated != null ? updated : combination));
        }
        return bindings;
    }

    /**
     * キーイベントに適合するキーバインドを取得する
     * @param event キーイベント
     * @return キーバインド
     */
    public Optional<KeyBinding> getBinding(KeyEvent event) {
        var o = custom.entrySet()
                .stream()
                .filter(e -> e.getValue().match(event))
                .findFirst()
                .map(Map.Entry::getKey);
        if (o.isPresent()) {
            LOGGER.info("get custom binding {}", o.get());
            return o;
        }

        return Arrays.stream(KeyBinding.values())
                .filter(b -> !custom.containsKey(b))
                .filter(b -> b.getDefaultCombination().match(event))
                .findFirst();
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
        LOGGER.info("reset key binding {}", binding);
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
        LOGGER.info("reset all bindings");
        custom.clear();
    }

}
