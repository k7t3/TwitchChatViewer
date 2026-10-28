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

/**
 * ユーザーデータを格納するSQLite形式のファイル
 */
public class UserDataFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDataFile.class);

    private Path filePath;

    private SQLiteDBConnector connector;

    public UserDataFile(String filePath) {
        this(Path.of(filePath));
    }

    public UserDataFile(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath).toAbsolutePath().normalize();
    }

    public synchronized DatabaseVersion connectDatabase(DatabaseVersion current) throws IOException {
        if (connector != null) {
            if (!connector.isConnected()) {
                connector.reconnect(getFilePath());
            }
            return DatabaseVersion.latest();
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
        connector = c;

        return version;
    }

    /**
     * DB を閉じてコピーし、同じコネクタを移動先へ接続する。
     * 既存の移動先を上書きせず、失敗時は元ファイルへの接続を復旧する。
     *
     * @param moveTo 移動先。同じファイルを指す場合は何もしない
     * @throws IOException コピーまたはファイルの同一性確認に失敗した場合
     */
    public synchronized void fileMove(Path moveTo) throws IOException {
        var moveFrom = this.filePath.toAbsolutePath().normalize();
        moveTo = Objects.requireNonNull(moveTo).toAbsolutePath().normalize();
        if (moveFrom.equals(moveTo) || (Files.exists(moveFrom) && Files.exists(moveTo)
                && Files.isSameFile(moveFrom, moveTo))) return;
        if (connector == null) {
            moveFile(moveFrom, moveTo, null);
        } else {
            // 保存タスクが close と reconnect の間に接続を使用しないようにする。
            synchronized (connector) {
                moveFile(moveFrom, moveTo, connector);
            }
        }
    }

    private void moveFile(Path moveFrom, Path moveTo, SQLiteDBConnector connector) throws IOException {
        if (connector != null) {
            connector.close();
        }

        boolean exists = Files.exists(moveFrom);
        try {
            if (exists) {
                Files.copy(moveFrom, moveTo);
            }
            if (connector != null) {
                connector.reconnect(moveTo);
            }
            this.filePath = moveTo;
        } catch (IOException | RuntimeException | Error failure) {
            if (connector != null && !connector.isConnected()) {
                try {
                    connector.reconnect(moveFrom);
                } catch (RuntimeException recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
            }
            throw failure;
        }
        if (exists) {
            try {
                Files.delete(moveFrom);
            } catch (IOException e) {
                LOGGER.warn("failed to delete an older user data file", e);
            }
        }
    }

    public synchronized void closeDatabase() {
        if (connector == null) return;
        connector.close();
    }

    public synchronized Path getFilePath() {
        return filePath;
    }

    public synchronized DBConnector getConnector() {
        return connector;
    }

}
