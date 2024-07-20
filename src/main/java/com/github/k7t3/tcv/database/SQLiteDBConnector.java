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

import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
        this.tableCreator = creator;
    }

    @Override
    protected Connection connectImpl() {
        try {
            var config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setJournalMode(SQLiteConfig.JournalMode.MEMORY);
            config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
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

    @Override
    public void connect() {
        // メモリモードのときか、対象のファイルが存在しないとき
        boolean empty = (filePath == null || !Files.exists(filePath));

        constructPath();
        super.connect();

        if (empty) {
            createTable();
        }

        // デフォルトでAutoCommitはFalse
        setAutoCommit(false);
    }

    public void reconnect(Path filePath) {
        if (isConnected()) throw new IllegalStateException("connected");
        this.filePath = filePath;
        connect();
    }

}
