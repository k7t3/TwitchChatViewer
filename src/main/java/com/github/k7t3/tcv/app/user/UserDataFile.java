package com.github.k7t3.tcv.app.user;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.database.TableCreator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ユーザーデータを格納するSQLite形式のファイル
 */
public class UserDataFile {

    private Path filePath;

    private final AtomicReference<SQLiteDBConnector> connectorRef = new AtomicReference<>();

    public UserDataFile(String filePath) {
        this(Path.of(filePath));
    }

    public UserDataFile(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath);
    }

    public FXTask<DBConnector> connectDatabaseAsync() {
        var connector = connectorRef.get();
        if (connector != null && connector.isConnected()) {
            return FXTask.of(connector);
        }
        var filePath = getFilePath();
        FXTask<DBConnector> t = FXTask.task(() -> {
            var parent = filePath.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            var c = new SQLiteDBConnector(filePath, TableCreator.DEFAULT);
            c.connect();
            connectorRef.set(c);
            return c;
        });
        t.runAsync();
        return t;
    }

    public FXTask<?> fileMoveAsync(Path moveTo) {
        var moveFrom = this.filePath;

        var t = FXTask.task(() -> {
            if (Files.exists(moveFrom)) {
                Files.move(moveFrom, moveTo);
            }
            var connector = connectorRef.get();
            if (connector != null) {
                connector.updateFilePath(moveTo);
            }
            return moveTo;
        });
        t.setSucceeded(() -> this.filePath = t.getValue());
        t.runAsync();
        return t;
    }

    public void closeDatabase() {
        var connector = connectorRef.get();
        if (connector == null) return;
        connector.close();
    }

    public Path getFilePath() {
        return filePath;
    }

    public DBConnector getConnector() {
        return connectorRef.get();
    }

}
