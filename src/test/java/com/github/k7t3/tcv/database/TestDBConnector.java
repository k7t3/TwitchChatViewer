package com.github.k7t3.tcv.database;

import com.github.k7t3.tcv.database.table.TableCreatorFactory;

public class TestDBConnector extends SQLiteDBConnector {

    public TestDBConnector() {
        super(TableCreatorFactory.create(DatabaseVersion.EMPTY, DatabaseVersion.latest()));
    }

}
