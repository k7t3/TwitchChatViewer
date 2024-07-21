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

package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.TableCreator;

/**
 * テーブルクリエイターを生成するファクトリ
 */
public class TableCreatorFactory {

    private TableCreatorFactory() {
    }

    /**
     * 現在のデータベースのバージョンと期待するデータベースのバージョンの
     * 組み合わせに一致したテーブルクリエイターを生成するメソッド
     * @param current 現在のバージョン
     * @param preferred 期待するバージョン
     * @return 適したテーブルクリエイター
     */
    public static TableCreator create(DatabaseVersion current, DatabaseVersion preferred) {
        if (current == preferred) throw new IllegalArgumentException("");
        if (current == DatabaseVersion.UNKNOWN) throw new IllegalArgumentException();
        if (current == DatabaseVersion.EMPTY)
            return new DefaultTableCreator(preferred);
        throw new UnsupportedOperationException();
    }

}
