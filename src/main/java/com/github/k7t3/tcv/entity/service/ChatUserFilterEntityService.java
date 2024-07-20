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
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChatUserFilterEntityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatUserFilterEntityService.class);

    private final DBConnector connector;

    public ChatUserFilterEntityService(DBConnector connector) {
        this.connector = connector;
    }

    public List<ChatUserFilterEntity> retrieveAll() {
        LOGGER.info("retrieve all");
        var query = "select user_id, user_name, comment from chat_user_filter;";

        var list = new ArrayList<ChatUserFilterEntity>();

        connector.query(query, rs -> {
            while (rs.next()) {
                var i = 0;
                var userId = rs.getString(++i);
                var userName = rs.getString(++i);
                var comment = rs.getString(++i);
                var entity = new ChatUserFilterEntity(userId, userName, comment);
                list.add(entity);
            }
        });

        return list;
    }

    public void save(ChatUserFilterEntity entity) {
        LOGGER.info("save entity {}", entity);
        var sql = "insert or replace into chat_user_filter values(?, ?, ?);";

        connector.prepared(sql, stmt -> {
            var i = 0;
            stmt.setString(++i, entity.userId());
            stmt.setString(++i, entity.userName());
            stmt.setString(++i, entity.comment());
            stmt.executeUpdate();
        });
        connector.commit();
    }

    public void delete(ChatUserFilterEntity entity) {
        LOGGER.info("delete entity {}", entity);
        var sql = "delete from chat_user_filter where user_id = ?;";

        connector.prepared(sql, stmt -> {
            stmt.setString(1, entity.userId());
            stmt.executeUpdate();
        });
        connector.commit();
    }

}
