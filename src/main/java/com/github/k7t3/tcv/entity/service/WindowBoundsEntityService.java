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

package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class WindowBoundsEntityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowBoundsEntityService.class);

    private static final double NaN = -1;

    private final DBConnector connector;

    public WindowBoundsEntityService(DBConnector connector) {
        this.connector = connector;
    }

    public void save(WindowBoundsEntity entity) {
        LOGGER.info("save window bounds {}", entity);
        if (entity.width() < 0 || entity.height() < 0) {
            throw new IllegalArgumentException(entity.toString());
        }

        var sql = "insert or replace into windows values(?, ?, ?, ?, ?, ?);";
        connector.prepared(sql, stmt -> {
            var i = 0;
            stmt.setString(++i, entity.identity());
            stmt.setDouble(++i, Double.isNaN(entity.x()) ? NaN : entity.x());
            stmt.setDouble(++i, Double.isNaN(entity.y()) ? NaN : entity.y());
            stmt.setDouble(++i, entity.width());
            stmt.setDouble(++i, entity.height());
            stmt.setBoolean(++i, entity.maximized());
            stmt.executeUpdate();
        });
        connector.commit();
    }

    public void delete(String identity) {
        LOGGER.info("delete window bounds {}", identity);

        var sql = "delete from windows where id = ?";
        connector.prepared(sql, stmt -> {
            stmt.setString(1, identity);
            stmt.executeUpdate();
        });
        connector.commit();
    }

    public void clear() {
        LOGGER.info("clear window bounds");

        var sql = "delete from windows;";
        connector.execute(sql);
        connector.commit();
    }

    public WindowBoundsEntity get(String identity) {
        if (Objects.requireNonNull(identity).isEmpty()) {
            throw new IllegalArgumentException("identity = " + identity);
        }

        WindowBoundsEntity[] entities = new WindowBoundsEntity[1];

        var query = "select id, x, y, width, height, maximized from windows where id = ?;";
        connector.prepared(query, stmt -> {
            stmt.setString(1, identity);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    entities[0] = WindowBoundsEntity.DEFAULT;
                    return;
                }
                var id = rs.getString("id");
                var x = rs.getDouble("x");
                var y = rs.getDouble("y");
                var width = rs.getDouble("width");
                var height = rs.getDouble("height");
                var maximized = rs.getBoolean("maximized");

                if (Double.isNaN(x)) x = Double.NaN;
                if (Double.isNaN(y)) y = Double.NaN;
                entities[0] = new WindowBoundsEntity(id, x, y, width, height, maximized);
            }
        });

        LOGGER.info("get window bounds {}({})", entities[0], identity);

        return entities[0];
    }

}
