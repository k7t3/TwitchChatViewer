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

public abstract class AbstractDBConnector implements DBConnector {

    private Connection connection = null;

    @Override
    public void connect() {
        if (connection != null) throw new IllegalStateException();
        this.connection = connectImpl();
    }

    protected abstract Connection connectImpl();

    @Override
    public boolean isConnected() {
        return connection != null;
    }

    private void validConnection() {
        if (connection == null) throw new IllegalStateException("connection is null");
    }

    @Override
    public void setAutoCommit(boolean autoCommit) {
        validConnection();
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void commit() {
        validConnection();
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void rollback() {
        validConnection();
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void execute(String sql) {
        validConnection();
        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void query(String query, SQLFunction<ResultSet> function) {
        validConnection();
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery(query)) {
            function.invoke(rs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void prepared(String prepared, SQLFunction<PreparedStatement> function) {
        validConnection();
        try (var stmt = connection.prepareStatement(prepared)) {
            function.invoke(stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        if (connection == null) return;
        try {
            connection.close();
            connection = null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
