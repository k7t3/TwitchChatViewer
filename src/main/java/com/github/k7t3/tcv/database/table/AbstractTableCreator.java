package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.TableCreator;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

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
        insertTableVersion(connector);
    }

    protected void insertTableVersion(DBConnector connector) {
        var sql = "insert table versions values(?, ?);";
        connector.prepared(sql, stmt -> {
            var i = 0;
            stmt.setInt(++i, preferredVersion);
            stmt.setTimestamp(++i, Timestamp.valueOf(LocalDateTime.now()));
            stmt.execute();
        });
        connector.commit();
    }

}
