package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.TableCreator;

public abstract class AbstractTableCreator implements TableCreator {

    protected final DatabaseVersion current;
    protected final DatabaseVersion preferred;

    public AbstractTableCreator(DatabaseVersion current, DatabaseVersion preferred) {
        this.current = current;
        this.preferred = preferred;
    }

    protected abstract void modifyTables(DBConnector connector);

    @Override
    public void create(DBConnector connector) {
        modifyTables(connector);
    }

}
