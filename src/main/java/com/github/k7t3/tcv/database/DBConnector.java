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

import java.io.Closeable;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface DBConnector extends Closeable {

    void connect();

    boolean isConnected();

    void setAutoCommit(boolean autoCommit);

    void commit();

    void rollback();

    void execute(String sql);

    void query(String query, SQLFunction<ResultSet> function);

    void prepared(String prepared, SQLFunction<PreparedStatement> function);

    @Override
    void close();
}
