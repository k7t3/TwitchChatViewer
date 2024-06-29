package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChatKeywordFilterEntityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatKeywordFilterEntityService.class);

    private final DBConnector connector;

    public ChatKeywordFilterEntityService(DBConnector connector) {
        this.connector = connector;
    }

    public List<ChatKeywordFilterEntity> retrieveAll() {
        LOGGER.info("retrieve all");
        var list = new ArrayList<ChatKeywordFilterEntity>();

        var query = "select id, type, keyword from chat_keyword_filter;";
        connector.query(query, rs -> {
            while (rs.next()) {
                var i = 0;

                var id = rs.getInt(++i);
                var type = rs.getInt(++i);
                var keyword = rs.getString(++i);

                var entity = new ChatKeywordFilterEntity(id, type, keyword);
                list.add(entity);
            }
        });

        return list;
    }

    public int insert(ChatKeywordFilterEntity entity) {
        LOGGER.info("insert entity {}", entity);
        var sql = "insert into chat_keyword_filter(type, keyword) values(?, ?);";

        int[] key = new int[1];
        connector.prepared(sql, stmt -> {
            var i = 0;
            stmt.setInt(++i, entity.type());
            stmt.setString(++i, entity.keyword());
            stmt.executeUpdate();

            try (var rs = stmt.getGeneratedKeys()) {
                key[0] = rs.getInt(1);
            }
        });
        connector.commit();

        return key[0];
    }

    public void update(ChatKeywordFilterEntity entity) {
        LOGGER.info("update entity {}", entity);
        var sql = "update chat_keyword_filter set type = ?, keyword = ? where id = ?;";

        connector.prepared(sql, stmt -> {
            var i = 0;
            stmt.setInt(++i, entity.type());
            stmt.setString(++i, entity.keyword());
            stmt.setInt(++i, entity.id());
            stmt.executeUpdate();
        });
        connector.commit();
    }

    public void delete(ChatKeywordFilterEntity entity) {
        LOGGER.info("delete entity {}", entity);
        var sql = "delete from chat_keyword_filter where id = ?;";

        connector.prepared(sql, stmt -> {
            stmt.setInt(1, entity.id());
            stmt.executeUpdate();
        });
        connector.commit();
    }

}
