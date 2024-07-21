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

package com.github.k7t3.tcv.app.user;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.database.table.TableCreatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ユーザーデータを格納するSQLite形式のファイル
 */
public class UserDataFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDataFile.class);

    private Path filePath;

    private final AtomicReference<SQLiteDBConnector> connectorRef = new AtomicReference<>();

    public UserDataFile(String filePath) {
        this(Path.of(filePath));
    }

    public UserDataFile(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath);
    }

    public DatabaseVersion connectDatabase(DatabaseVersion current) throws IOException {
        var connector = connectorRef.get();
        if (connector != null && connector.isConnected()) {
            return current;
        }
        var filePath = getFilePath();
        var parent = filePath.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        SQLiteDBConnector c;

        var version = DatabaseVersion.latest();
        if (current == version) {
            c = new SQLiteDBConnector(filePath);
        } else {
            var creator = TableCreatorFactory.create(current, version);
            c = new SQLiteDBConnector(filePath, creator);
        }

        c.connect();
        connectorRef.set(c);

        return version;
    }

    public void fileMove(Path moveTo) throws IOException {
        var moveFrom = this.filePath;

        boolean exists = Files.exists(moveFrom);
        if (exists) {
            Files.copy(moveFrom, moveTo);
        }
        var connector = connectorRef.get();
        if (connector != null) {
            connector.reconnect(moveTo);
            this.filePath = moveTo;
        }
        if (exists) {
            try {
                Files.delete(moveFrom);
            } catch (Exception e) {
                LOGGER.warn("failed to delete an older user data file.");
            }
        }
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
