package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.TableCreator;

public abstract class AbstractTableCreator implements TableCreator {

    protected final int currentVersion;
    protected final int preferredVersion;

    public AbstractTableCreator(int currentVersion, int preferredVersion) {
        this.currentVersion = currentVersion;
        this.preferredVersion = preferredVersion;
    }

    protected abstract void modifyTables(DBConnector connector);

    @Override
    public void create(DBConnector connector) {
        modifyTables(connector);
    }

}
