package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
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
                  group_users u
                left join
                  groups g
                on
                  u.group_id = g.id
                order by
                  g.updated_at desc;
                """;
        var list = new ArrayList<ChannelGroupEntity>();
        connector.query(query, rs -> {

            ChannelGroupEntity entity = null;
            while (rs.next()) {
                var id = rs.getString("id");
                var name = rs.getString("name");
                var comment = rs.getString("comment");
                var pinned = rs.getBoolean("pinned");
                var createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                var updatedAt = rs.getTimestamp("updated_at").toLocalDateTime();
                var userId = rs.getString("user_id");

                var uuid = UUID.fromString(id);

                if (entity == null || !entity.id().equals(uuid)) {
                    var ids = new HashSet<String>();
                    ids.add(userId);
                    entity = new ChannelGroupEntity(uuid, name, comment, pinned, createdAt, updatedAt, ids);
                    list.add(entity);
                } else {
                    entity.channelIds().add(userId);
                }
            }
        });

        LOGGER.info("{} groups retrieved", list.size());

        return list;
    }

    public void save(SaveType saveType, ChannelGroupEntity entity) {
        switch (saveType) {
            case INSERT -> insert(entity);
            case UPDATE -> update(entity);
        }
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

        connector.commit();

        LOGGER.info("{} inserted", entity);
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

        connector.commit();

        LOGGER.info("{} updated", entity);
    }

    public void remove(ChannelGroupEntity entity) {
        delete(entity);
    }

    private void delete(ChannelGroupEntity entity) {
        // グループユーザーはカスケード削除
        var deleteGroup = "delete from groups where id = ?;";
        connector.prepared(deleteGroup, stmt -> {
            stmt.setString(1, entity.id().toString());
            stmt.executeUpdate();
        });

        connector.commit();

        LOGGER.info("{} removed", entity);
    }

}
