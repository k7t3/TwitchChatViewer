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

package com.github.k7t3.tcv.prefs;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AppPreferences implements AutoCloseable {
    private final Preferences preferences;
    private final Path settingsLockFile;
    private final PreferenceStore store;

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPreferences.class);

    private final GeneralPreferences generalPreferences;

    private ChatPreferences chatPreferences;

    private KeyBindingPreferences keyBindingPreferences;

    private StatePreferences statePreferences;

    /** アプリ用ノードへの背景書込みを所有する。構築後、背景の起動処理でloadする。 */
    public AppPreferences() {
        this(Preferences.userRoot().node("/com/github/k7t3/tcv"),
                com.github.k7t3.tcv.app.core.OS.current().getApplicationDirectory().resolve("settings.lock"));
    }

    /** 借用ノードを同期的に更新するモデルを構築する。永続化の所有者は呼出側。 */
    public AppPreferences(Preferences preferences) {
        this(preferences, null);
    }

    /**
     * @param preferences 設定ノード。ノード自体の削除・解放は行わない
     * @param settingsLockFile 同じノードへ保存する全プロセスで共有するDBと独立したロック。
     *                         nullは借用ノード用のJVM内排他。構築時にはファイルを作らない
     */
    public AppPreferences(Preferences preferences, Path settingsLockFile) {
        this.preferences = Objects.requireNonNull(preferences);
        this.settingsLockFile = settingsLockFile == null ? null : settingsLockFile.toAbsolutePath().normalize();
        store = new PreferenceStore(preferences, this.settingsLockFile);
        generalPreferences = new GeneralPreferences(store);

        LOGGER.info("preferences initialized");
    }

    public Preferences getPreferences() {
        return preferences;
    }

    /** @return DBの移動先から独立した、設定保存用の共有ロック */
    public Optional<Path> settingsLockFile() { return Optional.ofNullable(settingsLockFile); }

    /**
     * UIで使用する既知の設定だけを背景で同期・読込する。資格情報や未知のキーは列挙しない。
     * FXプロパティの初期化前と起動時の復旧後に呼ぶ。借用ノードは即時読取を維持する。
     * @throws IllegalStateException FXスレッドから呼び出した場合、終了後、または保存先の失敗
     */
    public void load() {
        if (javafx.application.Platform.isFxApplicationThread()) throw new IllegalStateException("load preferences outside the FX thread");
        var keys = new java.util.HashSet<>(GeneralPreferences.storedKeys());
        keys.addAll(ChatPreferences.storedKeys());
        keys.addAll(StatePreferences.storedKeys());
        for (var binding : com.github.k7t3.tcv.app.keyboard.KeyBinding.values()) keys.add(binding.getId());
        store.load(java.util.Set.copyOf(keys));
    }

    public static void validateEditorValues(Map<String, Optional<String>> values) {
        var settings = new HashMap<String, PreferenceKey<?>>(GeneralPreferences.editorSettings());
        settings.putAll(ChatPreferences.editorSettings());
        var bindings = new HashMap<String, com.github.k7t3.tcv.app.keyboard.KeyBinding>();
        for (var binding : com.github.k7t3.tcv.app.keyboard.KeyBinding.values()) bindings.put(binding.getId(), binding);
        values.forEach((key, value) -> {
            if (key.length() > Preferences.MAX_KEY_LENGTH || value.filter(text -> text.length() > Preferences.MAX_VALUE_LENGTH).isPresent()) {
                throw new IllegalArgumentException("invalid editor preference: " + key);
            }
            var setting = settings.get(key);
            if (setting != null) value.ifPresent(setting::decode);
            else if (bindings.containsKey(key)) value.ifPresent(javafx.scene.input.KeyCombination::keyCombination);
            else throw new IllegalArgumentException("invalid editor preference: " + key);
        });
    }

    /**
     * SQLも確定した編集値を背景のキャッシュへ記録する。exclusiveの保存処理内で呼ぶ。
     * 保存中に受け付けた後続の通常変更は維持する。FXプロパティは変更しない。
     * @param values 永続化済みの編集値
     */
    public void recordCommittedValues(Map<String, Optional<String>> values) {
        validateEditorValues(values);
        store.committed(values);
    }

    /**
     * 永続化済みの値をFXのキャッシュに反映する。保存先へは書き戻さない。
     * 所有ノードでは、後続の通常変更や別の保存で置き換わった値を反映しない。
     * @param values 永続化済みの編集値
     * @return 現在も有効で、UIへ適用できる編集値
     */
    public Map<String, Optional<String>> applyCommittedValues(Map<String, Optional<String>> values) {
        if (!javafx.application.Platform.isFxApplicationThread()) throw new IllegalStateException("use the FX application thread");
        validateEditorValues(values);
        var current = store.currentProjection(values);
        generalPreferences.applyCommittedValues(current);
        if (chatPreferences != null) chatPreferences.applyCommittedValues(current);
        return current;
    }

    /** 背景から受付済み変更の永続化を待ち、書込・flushの失敗を返す。FXプロパティは読み直さない。 */
    public void save() { store.save(); LOGGER.info("save preferences"); }

    /** 新規変更を止め、受付済みの保存を背景から待つ。借用ノード自体は削除・解放しない。 */
    @Override public void close() { store.close(this::save); }

    /**
     * 設定エディターのDB操作を、受付済みの通常設定変更に続けて実行する。背景から呼ぶ。
     * 同じ保存処理からの再入は同じ排他範囲で実行し、自分自身の完了を待たない。
     * @param operation 保存処理。返るまで共有ファイルを所有する。DB、ノードの順で排他すること
     * @return 保存処理の結果
     * @param <T> 結果の型
     */
    public <T> T exclusive(java.util.function.Supplier<T> operation) { return store.exclusive(operation); }

    public GeneralPreferences getGeneralPreferences() {
        return generalPreferences;
    }

    public ChatPreferences getChatPreferences() {
        if (chatPreferences == null) {
            chatPreferences = new ChatPreferences(store);
        }
        return chatPreferences;
    }

    public KeyBindingPreferences getKeyBindingPreferences() {
        if (keyBindingPreferences == null) {
            keyBindingPreferences = new KeyBindingPreferences(store);
        }
        return keyBindingPreferences;
    }

    public StatePreferences getStatePreferences() {
        if (statePreferences == null)
            statePreferences = new StatePreferences(store);
        return statePreferences;
    }

    // ******************** PROPERTIES ********************


}
