package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.OpeningChannelEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpeningChannelEntityServiceTest {

    private DBConnector connector;

    private OpeningChannelEntityService service;

    @BeforeEach
    void setUp() {
        connector = new TestDBConnector();
        connector.connect();
        service = new OpeningChannelEntityService(connector);
    }

    @AfterEach
    void tearDown() {
        connector.close();
    }

    @Test
    void save() {
        var entity = new OpeningChannelEntity("userId", 1, "windowId");
        service.save(entity);

        var get = service.get(entity.userId());
        assertTrue(get.isPresent());
        assertEquals(entity, get.get());
    }

    @Test
    void saveAll() {
        var entity1 = new OpeningChannelEntity("first", 1, "second");
        var entity2 = new OpeningChannelEntity("second", 2, "second");
        var all = List.of(entity1, entity2);
        service.saveAll(all);

        var get = service.getAll(all.stream().map(OpeningChannelEntity::userId).toList());
        assertEquals(all.size(), get.size());
        assertTrue(get.contains(entity1));
        assertTrue(get.contains(entity2));

        var none = new OpeningChannelEntity("", 0, "");
        assertFalse(get.contains(none));
    }

    @Test
    void delete() {
        var entity1 = new OpeningChannelEntity("first", 1, "second");
        var entity2 = new OpeningChannelEntity("second", 2, "second");
        var all = List.of(entity1, entity2);
        service.saveAll(all);

        var get = service.getAll(all.stream().map(OpeningChannelEntity::userId).toList());
        assertEquals(all.size(), get.size());
        assertTrue(get.contains(entity1));
        assertTrue(get.contains(entity2));

        service.delete(entity1);
        get = service.getAll(all.stream().map(OpeningChannelEntity::userId).toList());
        assertEquals(1, get.size());
        assertFalse(get.contains(entity1));
        assertTrue(get.contains(entity2));

        var next = new OpeningChannelEntity("next", 3, "third");
        service.save(entity1);
        service.save(next);

        all = List.of(entity1, entity2, next);
        service.deleteAll(List.of(entity1, entity2));
        get = service.getAll(all.stream().map(OpeningChannelEntity::userId).toList());
        assertEquals(1, get.size());
        assertFalse(get.contains(entity1));
        assertFalse(get.contains(entity2));
        assertTrue(get.contains(next));
    }
}