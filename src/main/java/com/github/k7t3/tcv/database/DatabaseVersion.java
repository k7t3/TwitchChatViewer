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
     * <li>キーワードフィルタ</li>
     * <li>ユーザーフィルタ</li>
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
