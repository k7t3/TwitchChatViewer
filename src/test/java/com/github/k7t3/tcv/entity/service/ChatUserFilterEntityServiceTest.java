package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatUserFilterEntityServiceTest {

    private DBConnector connector;
    private ChatUserFilterEntityService service;

    @BeforeEach
    void setUp() {
        connector = new TestDBConnector();
        service = new ChatUserFilterEntityService(connector);
        connector.connect();
    }

    @AfterEach
    void tearDown() {
        connector.close();
    }

    @Test
    void save() {
        // insert
        var entity = new ChatUserFilterEntity("userId", "userName", "comment");
        service.save(entity);

        var list = service.retrieveAll();
        assertEquals(1, list.size());
        assertEquals(entity, list.getFirst());

        // update
        entity = new ChatUserFilterEntity(entity.userId(), entity.userName(), "updated a comment");
        service.save(entity);

        list = service.retrieveAll();
        assertEquals(1, list.size());
        assertEquals(entity, list.getFirst());
    }

    @Test
    void delete() {
        var entity = new ChatUserFilterEntity("userId", "userName", "comment");
        service.save(entity);

        var list = service.retrieveAll();
        assertEquals(1, list.size());

        service.delete(entity);

        list = service.retrieveAll();
        assertTrue(list.isEmpty());
    }
}