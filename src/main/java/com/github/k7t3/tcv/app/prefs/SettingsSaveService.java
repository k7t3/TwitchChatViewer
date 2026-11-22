package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.entity.service.SettingsJournal;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.PreferenceProjection;
import com.github.k7t3.tcv.prefs.PreferenceValuesCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 編集値とSQLを保存し、起動時・保存前に未完了の設定投影を復旧する。接続は借用する。 */
public final class SettingsSaveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsSaveService.class);
    public record SavedSettings(SettingsDraft draft, ChatFilterEntityStore.Snapshot filters) {}

    private final AppPreferences preferences;
    private final ChatFilterEntityStore filters;
    private final SettingsJournal journal;

    /** 同じDBに属するフィルターと設定を借用する。構築だけではI/Oしない。 */
    public SettingsSaveService(AppPreferences preferences, ChatFilterEntityStore filters) {
        this.preferences = java.util.Objects.requireNonNull(preferences);
        this.filters = java.util.Objects.requireNonNull(filters);
        journal = filters.settingsJournal();
    }

    /**
     * 編集キーの旧値と新値を記録してから保存し、成功後にUIへ渡す不変の結果を返す。
     * 確定済みの保存は、通常の後始末失敗があっても結果を返し、復旧情報とログを残す。
     * @param draft FX側で確定した編集内容。I/Oを行うため背景で渡すこと
     * @return 保存した編集内容と確定したフィルター
     */
    public SavedSettings save(SettingsDraft draft) {
        AppPreferences.validateEditorValues(draft.values());
        var completed = new SavedSettings[1];
        try {
            return exclusive(() -> saveLocked(draft, completed));
        } catch (RuntimeException failure) {
            if (completed[0] == null) throw failure;
            LOGGER.warn("Could not clean up committed settings recovery information", failure);
            return completed[0];
        }
    }

    /** 保存要求を発行せずに未完了の設定投影を復旧する。背景の起動処理から呼び出す。 */
    public void recover() {
        exclusive(() -> {
            var node = preferences.getPreferences();
            synchronized (node) {
                sync(node);
                recoverLocked(node);
            }
            return null;
        });
    }

    private <T> T exclusive(Supplier<T> operation) {
        return preferences.exclusive(() -> journal.exclusive(operation));
    }

    private SavedSettings saveLocked(SettingsDraft draft, SavedSettings[] completed) {
        var node = preferences.getPreferences();
        synchronized (node) {
            sync(node);
            recoverLocked(node);
            var previous = new HashMap<String, Optional<String>>();
            draft.values().keySet().forEach(key -> previous.put(key, Optional.ofNullable(node.get(key, null))));
            var previousOwners = PreferenceProjection.snapshotOwners(node);
            var operation = UUID.randomUUID();
            journal.prepare(new SettingsJournal.Entry(operation, node.absolutePath(), node.isUserNode(),
                    PreferenceValuesCodec.encode(previous), PreferenceValuesCodec.encode(draft.values()), false,
                    Optional.of(PreferenceValuesCodec.encode(PreferenceProjection.owners(node)))));
            var changed = new boolean[1];
            final ChatFilterEntityStore.Snapshot result;
            try {
                result = filters.save(draft.filters(), () -> {
                    changed[0] = true;
                    PreferenceProjection.beginProjection(node, operation);
                    PreferenceProjection.claim(node, operation, draft.values());
                    write(node, draft.values());
                    preferences.save();
                    journal.markCommitted(operation);
                });
            } catch (RuntimeException | Error failure) {
                try {
                    var committed = journal.pending().filter(entry -> entry.operation().equals(operation))
                            .map(SettingsJournal.Entry::committed).orElse(false);
                    if (!committed) {
                        // 失敗した編集を、他DBの復旧より新しい確定済み変更として残さない。
                        if (changed[0]) restore(node, previous, () -> {
                            PreferenceProjection.restoreOwners(node, previousOwners);
                            PreferenceProjection.completeProjection(node, operation);
                        });
                        journal.clear(operation);
                    }
                } catch (RuntimeException | Error restore) {
                    if (restore != failure) failure.addSuppressed(restore);
                }
                throw failure;
            }
            completed[0] = new SavedSettings(draft, result);
            preferences.recordCommittedValues(draft.values());
            PreferenceProjection.completeProjection(node, operation);
            preferences.save();
            journal.clear(operation);
            return completed[0];
        }
    }

    private static void sync(Preferences node) {
        try { node.sync(); }
        catch (BackingStoreException failure) { throw new RuntimeException(failure); }
    }

    private void recoverLocked(Preferences node) {
        var pending = journal.pending();
        if (pending.isEmpty()) return;
        var entry = pending.orElseThrow();
        if (!entry.nodePath().equals(node.absolutePath()) || entry.userNode() != node.isUserNode()) {
            throw new IllegalStateException("settings recovery belongs to another preferences node");
        }
        PreferenceProjection.isPending(node, entry.operation());
        var before = PreferenceValuesCodec.decode(entry.before());
        var after = PreferenceValuesCodec.decode(entry.after());
        if (!before.keySet().equals(after.keySet())) throw new IllegalArgumentException("settings recovery keys differ");
        var keys = new HashMap<String, Optional<String>>();
        before.keySet().forEach(key -> keys.put(key, Optional.empty()));
        // 旧値は型が不正でも補償できるよう、キーだけを検証してrawのまま保持する。
        AppPreferences.validateEditorValues(keys);
        AppPreferences.validateEditorValues(after);
        var previousOwners = entry.committed() ? Optional.<Map<String, Optional<String>>>empty()
                : entry.previousOwners().map(PreferenceProjection::decodeOwners);
        var owners = PreferenceProjection.owners(node);
        var values = new HashMap<>(entry.committed() ? after : before);
        // 後続の確定保存を維持し、未完了の所有者に遮られた復旧記録は破棄しない。
        values.keySet().removeIf(key -> {
            var owner = owners.get(key);
            // 先行する操作の補償で所有が消えた場合、その操作の古い投影を再び有効にしない。
            if (owner == null) return previousOwners.map(previous -> previous.containsKey(key)).orElse(false);
            if (owner.equals(Optional.of(entry.operation().toString()))) return false;
            // この投影が始まる前、または補償で既に旧所有へ戻ったキーは書き戻さない。
            if (previousOwners.map(previous -> owner.equals(previous.get(key))).orElse(false)) return true;
            if (PreferenceProjection.isPending(node, UUID.fromString(owner.orElseThrow()))) {
                throw new IllegalStateException("another pending settings projection must be recovered first");
            }
            return true;
        });
        restore(node, values, () -> {
            previousOwners.ifPresent(previous -> PreferenceProjection.restoreOwners(node, previous, values.keySet()));
            PreferenceProjection.completeProjection(node, entry.operation());
        });
        journal.clear(entry.operation());
    }

    private void restore(Preferences node, Map<String, Optional<String>> values, Runnable... additional) {
        var actions = new ArrayList<Runnable>();
        values.forEach((key, value) -> actions.add(() -> write(node, Map.of(key, value))));
        CloseActions.runAll(() -> {
            CloseActions.runAll(actions.toArray(Runnable[]::new));
            // 値を戻せなければ、この操作の所有を残して次回の復旧対象にする。
            CloseActions.runAll(additional);
        }, preferences::save);
    }

    private static void write(Preferences node, Map<String, Optional<String>> values) {
        values.forEach((key, value) -> { if (value.isPresent()) node.put(key, value.get()); else node.remove(key); });
    }
}
