package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.TableCreator;

/**
 * テーブルクリエイターを生成するファクトリ
 */
public class TableCreatorFactory {

    private TableCreatorFactory() {
    }

    public static TableCreator create(DatabaseVersion current, DatabaseVersion create) {
        return create(current.getVersion(), create.getVersion());
    }

    private static TableCreator create(int current, int preferred) {
        if (current == preferred) throw new IllegalArgumentException();
        if (current < 0) throw new IllegalArgumentException();
        if (current == 0)
            return new DefaultTableCreator(preferred);
        throw new UnsupportedOperationException();
    }

}
