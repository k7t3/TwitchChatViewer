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
