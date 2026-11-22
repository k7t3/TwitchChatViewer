package com.github.k7t3.tcv.prefs;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.prefs.Preferences;

/** 通常プロパティの保存と設定エディターが共有する、キーごとの更新所有情報。 */
public final class PreferenceProjection {
    private static final String OWNERS = "settings.projection.owners";
    private static final String PENDING_PREFIX = "settings.projection.pending.";

    private PreferenceProjection() {}

    /**
     * 他DBの復旧が未完了の投影を後続の確定保存と区別できるようにする。
     * @param node monitorと保存用の排他を呼出側で保持するノード
     * @param operation これから投影する操作。flushは呼出側が行う
     */
    public static void beginProjection(Preferences node, UUID operation) {
        node.put(PENDING_PREFIX + operation, "1");
    }

    /**
     * @param node monitorと保存用の排他を呼出側で保持するノード
     * @param operation 所有情報から得た操作
     * @return 確定または補償の完了が未通知ならtrue。旧記録に印がなければfalse
     * @throws IllegalArgumentException 未対応の未完了印の場合
     */
    public static boolean isPending(Preferences node, UUID operation) {
        var value = node.get(PENDING_PREFIX + operation, null);
        if (value == null) return false;
        if (!value.equals("1")) throw new IllegalArgumentException("invalid pending settings projection");
        return true;
    }

    /**
     * @param node monitorと保存用の排他を呼出側で保持するノード
     * @param operation 確定または補償を完了した操作。flushは呼出側が行う
     */
    public static void completeProjection(Preferences node, UUID operation) {
        node.remove(PENDING_PREFIX + operation);
    }

    /**
     * @param node monitorと保存用の排他を呼出側で保持するノード
     * @return 補償用の所有情報。未作成と空の記録を区別して保持する
     */
    public static Optional<String> snapshotOwners(Preferences node) { return Optional.ofNullable(node.get(OWNERS, null)); }

    /**
     * 同じ排他範囲の失敗した更新を補償する。flushは呼出側が行う。
     * @param node monitorと保存用の排他を呼出側で保持するノード
     * @param previous 更新前に取得した所有情報
     */
    public static void restoreOwners(Preferences node, Optional<String> previous) {
        if (previous.isPresent()) node.put(OWNERS, previous.orElseThrow());
        else node.remove(OWNERS);
    }

    /**
     * 既存の所有情報を検証して読み出す。呼出側がnodeのmonitorを保持する。
     * @param node 借用する設定ノード
     * @return キーごとの更新識別子。返したMapの変更はノードへ反映しない
     */
    public static Map<String, Optional<String>> owners(Preferences node) {
        var encoded = node.get(OWNERS, null);
        if (encoded == null) return new HashMap<>();
        return decodeOwners(Base64.getDecoder().decode(encoded));
    }

    /**
     * @param encoded 既存の版1形式で保存された所有情報
     * @return UUIDまで検証済みの所有情報。返したMapは変更しても保存先に作用しない
     */
    public static Map<String, Optional<String>> decodeOwners(byte[] encoded) {
        var result = new HashMap<>(PreferenceValuesCodec.decode(encoded));
        result.values().forEach(value -> UUID.fromString(value.orElseThrow(
                () -> new IllegalArgumentException("missing settings projection owner"))));
        return result;
    }

    /**
     * 復旧したキーだけを以前の所有へ戻す。後続保存が所有する他のキーを維持する。
     * @param node monitorと保存用の排他を呼出側で保持するノード
     * @param previous 検証済みの更新前所有情報
     * @param keys 値の補償に成功したキー。flushは呼出側が行う
     */
    public static void restoreOwners(Preferences node, Map<String, Optional<String>> previous, Set<String> keys) {
        var owners = owners(node);
        keys.forEach(key -> {
            if (previous.containsKey(key)) owners.put(key, previous.get(key));
            else owners.remove(key);
        });
        if (owners.isEmpty()) node.remove(OWNERS);
        else node.put(OWNERS, Base64.getEncoder().encodeToString(PreferenceValuesCodec.encode(owners)));
    }

    /**
     * 更新するキーの所有を記録する。値の保存とflushは呼出側が行う。
     * 呼出側がnodeのmonitorを保持し、プロセス間の排他も必要に応じて確保する。
     * @param node 借用する設定ノード
     * @param operation この更新だけを識別するUUID
     * @param values 更新するキー。既存の他キーの所有は維持する
     */
    public static void claim(Preferences node, UUID operation, Map<String, Optional<String>> values) {
        if (values.isEmpty()) return;
        var owners = owners(node);
        values.keySet().forEach(key -> owners.put(key, Optional.of(operation.toString())));
        node.put(OWNERS, Base64.getEncoder().encodeToString(PreferenceValuesCodec.encode(owners)));
    }
}
