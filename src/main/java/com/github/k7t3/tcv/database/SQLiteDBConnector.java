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

import com.github.k7t3.tcv.database.table.TableCreatorFactory;
import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

public class SQLiteDBConnector extends AbstractDBConnector {

    private static final String DRIVER = "org.sqlite.JDBC";
    private static final String HEADER = "jdbc:sqlite:";

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Path filePath;

    private String connectionURL;

    private final TableCreator tableCreator;

    @Override
    public synchronized Optional<Path> storagePath() {
        return Optional.ofNullable(filePath).map(path -> path.toAbsolutePath().normalize());
    }

    /**
     * テスト用のインメモリモードコンストラクタ
     */
    SQLiteDBConnector(TableCreator creator) {
        this.filePath = null;
        this.tableCreator = creator;
    }

    public SQLiteDBConnector(Path filePath) {
        this(filePath, null);
    }

    public SQLiteDBConnector(Path filePath, TableCreator creator) {
        this.filePath = filePath;
        this.tableCreator = creator != null ? creator
                : TableCreatorFactory.create(DatabaseVersion.EMPTY, DatabaseVersion.latest());
    }

    @Override
    protected Connection connectImpl() {
        try {
            var config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            // ファイル DB はクラッシュ後に復旧できるジャーナルと同期を維持する。
            config.setJournalMode(filePath == null ? SQLiteConfig.JournalMode.MEMORY : SQLiteConfig.JournalMode.DELETE);
            config.setSynchronous(filePath == null ? SQLiteConfig.SynchronousMode.OFF : SQLiteConfig.SynchronousMode.FULL);
            config.setDatePrecision(SQLiteConfig.DatePrecision.MILLISECONDS.getValue());

            return DriverManager.getConnection(connectionURL, config.toProperties());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void constructPath() {
        if (filePath == null) {
            this.connectionURL = HEADER + ":memory:";
        } else {
            this.connectionURL = HEADER + filePath.toAbsolutePath();
        }
    }

    private void createTable() {
        this.tableCreator.create(this);
    }

    private boolean hasTables() {
        var exists = new boolean[1];
        query("select 1 from sqlite_master where type = 'table' and name not like 'sqlite_%' limit 1;",
                rows -> exists[0] = rows.next());
        return exists[0];
    }

    @Override
    public synchronized void connect() {
        constructPath();
        super.connect();

        try {
            // スキーマ作成も更新と同じ境界で確定し、途中の DDL を残さない。
            setAutoCommit(false);
            transaction(() -> { if (!hasTables()) createTable(); });
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public synchronized void reconnect(Path filePath) {
        if (isConnected()) throw new IllegalStateException("connected");
        this.filePath = filePath;
        connect();
    }

}
