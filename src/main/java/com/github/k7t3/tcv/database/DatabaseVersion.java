package com.github.k7t3.tcv.database;

import java.util.Arrays;

public enum DatabaseVersion {

    UNKNOWN(-1),

    EMPTY(0),

    /**
     * 初期バージョンのテーブル
     * <li>グループ</li>
     * <li>グループ・ユーザー</li>
     * <li>ウィンドウ</li>
     */
    V_1(1);

    private final int version;

    DatabaseVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public static DatabaseVersion latest() {
        return V_1;
    }

    public static DatabaseVersion of(int version) {
        return Arrays.stream(DatabaseVersion.values())
                .filter(v -> v.version == version)
                .findFirst()
                .orElse(UNKNOWN);
    }
}
