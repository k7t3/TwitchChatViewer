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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public abstract class AbstractDBConnector implements DBConnector {

    private Connection connection = null;
    private boolean transactionActive;
    private boolean manualWork;
    private boolean invalid;

    @Override
    public synchronized void connect() {
        if (connection != null) throw new IllegalStateException();
        this.connection = connectImpl();
        invalid = false;
    }

    protected abstract Connection connectImpl();

    @Override
    public synchronized boolean isConnected() {
        return connection != null && !invalid;
    }

    private void validConnection() {
        if (connection == null) throw new IllegalStateException("connection is null");
        if (invalid) throw new IllegalStateException("connection is invalid");
    }

    @Override
    public synchronized void setAutoCommit(boolean autoCommit) {
        validConnection();
        try {
            connection.setAutoCommit(autoCommit);
            if (autoCommit) manualWork = false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void commit() {
        validConnection();
        try {
            connection.commit();
            manualWork = false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void rollback() {
        validConnection();
        try {
            connection.rollback();
            manualWork = false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void execute(String sql) {
        validConnection();
        markManualWork();
        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void query(String query, SQLFunction<ResultSet> function) {
        validConnection();
        markManualWork();
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery(query)) {
            function.invoke(rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void prepared(String prepared, SQLFunction<PreparedStatement> function) {
        validConnection();
        markManualWork();
        try (var stmt = connection.prepareStatement(prepared)) {
            function.invoke(stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void readTransaction(Runnable action) {
        validConnection();
        Objects.requireNonNull(action);
        if (transactionActive || manualWork) {
            action.run();
            return;
        }
        final boolean autoCommit;
        try {
            autoCommit = connection.getAutoCommit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (autoCommit) setAutoCommit(false);
        Throwable failure = null;
        try {
            transaction(action);
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            if (autoCommit) {
                try {
                    setAutoCommit(true);
                } catch (RuntimeException | Error restoreFailure) {
                    if (failure == null) throw restoreFailure;
                    failure.addSuppressed(restoreFailure);
                }
            }
        }
    }

    private void markManualWork() {
        try {
            // 生のSQL APIが開始した範囲は、呼出元のcommit/rollbackまで借用する。
            if (!transactionActive && !connection.getAutoCommit()) manualWork = true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void transaction(Runnable action) {
        validConnection();
        Objects.requireNonNull(action);
        if (transactionActive) throw new IllegalStateException("nested transaction");
        try {
            if (connection.getAutoCommit()) throw new IllegalStateException("auto-commit must be disabled");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        transactionActive = true;
        try {
            action.run();
            commit();
        } catch (RuntimeException | Error failure) {
            try {
                rollback();
            } catch (RuntimeException | Error rollbackFailure) {
                invalid = true;
                if (rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
                // 結果が不明な接続を再利用すると、後続の読取が未確定更新をcommitできてしまう。
                try {
                    close();
                } catch (RuntimeException | Error closeFailure) {
                    if (closeFailure != failure) failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        } finally {
            transactionActive = false;
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
            connection = null;
            manualWork = false;
            invalid = false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
