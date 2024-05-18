package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.entity.OpeningChannelEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class OpeningChannelEntityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpeningChannelEntityService.class);

    private final DBConnector connector;

    private final ReentrantLock lock = new ReentrantLock(true);

    public OpeningChannelEntityService(DBConnector connector) {
        this.connector = connector;
    }

    public Optional<OpeningChannelEntity> get(String userId) {
        LOGGER.info("get opening channel {}", userId);

        OpeningChannelEntity[] entity = new OpeningChannelEntity[1];
        lock.lock();
        try {
            var query = """
                    select
                        order_no,
                        window_id
                    from
                        opening_channels
                    where
                        user_id = ?;
                    """;
            connector.prepared(query, stmt -> {
                stmt.setString(1, userId);
                try (var rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                    var orderNo = rs.getInt(1);
                    var windowId = rs.getString(2);
                    entity[0] = new OpeningChannelEntity(userId, orderNo, windowId);
                }
            });
        } finally {
            lock.unlock();
        }
        return Optional.ofNullable(entity[0]);
    }

    public List<OpeningChannelEntity> getAll(List<String> userIds) {
        LOGGER.info("get opening channels {}", userIds);
        if (userIds.isEmpty()) {
            return List.of();
        }

        var entities = new ArrayList<OpeningChannelEntity>();
        lock.lock();
        try {
            var query = """
                    select
                        user_id,
                        order_no,
                        window_id
                    from
                        opening_channels
                    where
                        user_id in %s;
                    """;
            // SQLite JDBCは配列のプレースホルダをサポートしていないので自分で作る
            var ids = userIds.stream()
                    .map("'%s'"::formatted)
                    .collect(Collectors.joining(","));
            var param = "(" + ids + ")";
            connector.query(query.formatted(param), rs -> {
                while (rs.next()) {
                    var i = 0;
                    var userId = rs.getString(++i);
                    var orderNo = rs.getInt(++i);
                    var windowId = rs.getString(++i);
                    entities.add(new OpeningChannelEntity(userId, orderNo, windowId));
                }
            });
        } finally {
            lock.unlock();
        }

        return entities;
    }

    public void save(OpeningChannelEntity entity) {
        LOGGER.info("save opening channel {}", entity);

        lock.lock();
        try {
            var sql = "insert or replace into opening_channels values(?, ?, ?);";
            connector.prepared(sql, (stmt) -> {
                var i = 0;
                stmt.setString(++i, entity.userId());
                stmt.setInt(++i, entity.order());
                stmt.setString(++i, entity.windowId());
                stmt.executeUpdate();
            });
            connector.commit();
        } finally {
            lock.unlock();
        }
    }

    public void saveAll(List<OpeningChannelEntity> entities) {
        LOGGER.info("save opening channels {}", entities);
        if (entities.isEmpty()) {
            return;
        }

        lock.lock();
        try {
            var sql = "insert or replace into opening_channels values(?, ?, ?);";
            connector.prepared(sql, (stmt) -> {
                for (var entity : entities) {
                    var i = 0;
                    stmt.setString(++i, entity.userId());
                    stmt.setInt(++i, entity.order());
                    stmt.setString(++i, entity.windowId());
                    stmt.executeUpdate();
                }
            });
            connector.commit();
        } finally {
            lock.unlock();
        }
    }

    public void delete(OpeningChannelEntity entity) {
        LOGGER.info("closed opening channel {}", entity);

        lock.lock();
        try {
            var sql = "delete from opening_channels where user_id = ?;";
            connector.prepared(sql, (stmt) -> {
                var i = 0;
                stmt.setString(++i, entity.userId());
                stmt.executeUpdate();
            });
            connector.commit();
        } finally {
            lock.unlock();
        }
    }

    public void deleteAll(List<OpeningChannelEntity> entities) {
        LOGGER.info("closed opening channels {}", entities);
        if (entities.isEmpty()) {
            return;
        }

        lock.lock();
        try {
            var sql = "delete from opening_channels where user_id = ?;";
            connector.prepared(sql, (stmt) -> {
                for (var entity : entities) {
                    var i = 0;
                    stmt.setString(++i, entity.userId());
                    stmt.executeUpdate();
                }
            });
            connector.commit();
        } finally {
            lock.unlock();
        }
    }

}
