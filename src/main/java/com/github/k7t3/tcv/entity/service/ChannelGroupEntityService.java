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
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * チャンネルグループに関するサービス
 */
public class ChannelGroupEntityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelGroupEntityService.class);

    /**
     * チャンネルグループを永続化するDBのコネクタ
     */
    private final DBConnector connector;

    public ChannelGroupEntityService(DBConnector connector) {
        this.connector = connector;
    }

    public List<ChannelGroupEntity> retrieveAll() {
        var query = """
                select
                  g.id,
                  g.name,
                  g.comment,
                  g.pinned,
                  g.created_at,
                  g.updated_at,
                  u.user_id
                from
                  groups g
                left join
                  group_users u
                on
                  u.group_id = g.id
                order by
                  g.updated_at desc;
                """;
        // 同じ更新日時の行は別グループの行と混在できる。隣接ではなく親IDで集約する。
        var groups = new LinkedHashMap<UUID, ChannelGroupEntity>();
        connector.readTransaction(() -> connector.query(query, rs -> {
            while (rs.next()) {
                var id = rs.getString("id");
                var name = rs.getString("name");
                var comment = rs.getString("comment");
                var pinned = rs.getBoolean("pinned");
                var createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                var updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
                var userId = rs.getString("user_id");

                var uuid = UUID.fromString(id);

                var entity = groups.get(uuid);
                if (entity == null) {
                    var ids = new HashSet<String>();
                    entity = new ChannelGroupEntity(uuid, name, comment, pinned, createdAt, updatedAt, ids);
                    groups.put(uuid, entity);
                }
                if (userId != null) entity.channelIds().add(userId);
            }
        }));

        LOGGER.info("{} groups retrieved", groups.size());

        return new ArrayList<>(groups.values());
    }

    public void save(SaveType saveType, ChannelGroupEntity entity) {
        connector.transaction(() -> {
            switch (saveType) {
                case INSERT -> insert(entity);
                case UPDATE -> update(entity);
            }
        });
        LOGGER.info("{} {}", entity, saveType == SaveType.INSERT ? "inserted" : "updated");
    }

    private void insert(ChannelGroupEntity entity) {
        var groupInsert = "insert into groups(id, name, comment, pinned, created_at, updated_at) values(?, ?, ?, ?, ?, ?);";

        connector.prepared(groupInsert, stmt -> {
            int i = 0;
            stmt.setString(++i, entity.id().toString());
            stmt.setString(++i, entity.name());
            stmt.setString(++i, entity.comment());
            stmt.setBoolean(++i, entity.pinned());
            stmt.setTimestamp(++i, Timestamp.valueOf(entity.createdAt()));
            stmt.setTimestamp(++i, Timestamp.valueOf(entity.updatedAt()));
            stmt.executeUpdate();
        });

        var groupUserInsert = "insert into group_users values(?, ?);";

        connector.prepared(groupUserInsert, stmt -> {
            for (var userId : entity.channelIds()) {
                int i = 0;
                stmt.setString(++i, entity.id().toString());
                stmt.setString(++i, userId);
                stmt.executeUpdate();
            }
        });

    }

    private void update(ChannelGroupEntity entity) {
        var groupUpdate = """
                update groups
                set
                  name = ?,
                  comment = ?,
                  pinned = ?,
                  updated_at = ?
                where
                  id = ?;
                """;
        connector.prepared(groupUpdate, stmt -> {
            int i = 0;
            stmt.setString(++i, entity.name());
            stmt.setString(++i, entity.comment());
            stmt.setBoolean(++i, entity.pinned());
            stmt.setTimestamp(++i, Timestamp.valueOf(entity.updatedAt()));
            stmt.setString(++i, entity.id().toString());
            stmt.executeUpdate();
        });

        var deleteGroupUsers = """
                delete from group_users where group_id = ?;
                """;
        connector.prepared(deleteGroupUsers, stmt -> {
            stmt.setString(1, entity.id().toString());
            stmt.executeUpdate();
        });

        var groupUserInsert = "insert into group_users values(?, ?);";

        connector.prepared(groupUserInsert, stmt -> {
            for (var userId : entity.channelIds()) {
                int i = 0;
                stmt.setString(++i, entity.id().toString());
                stmt.setString(++i, userId);
                stmt.executeUpdate();
            }
        });

    }

    public void remove(ChannelGroupEntity entity) {
        connector.transaction(() -> delete(entity));
        LOGGER.info("{} removed", entity);
    }

    private void delete(ChannelGroupEntity entity) {
        // グループユーザーはカスケード削除
        var deleteGroup = "delete from groups where id = ?;";
        connector.prepared(deleteGroup, stmt -> {
            stmt.setString(1, entity.id().toString());
            stmt.executeUpdate();
        });

    }

}
