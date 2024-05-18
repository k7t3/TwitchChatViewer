package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowBoundsEntityServiceTest {

    private DBConnector connector;

    private WindowBoundsEntityService service;

    @BeforeEach
    void setUp() {
        connector = new TestDBConnector();
        connector.connect();
        service = new WindowBoundsEntityService(connector);
    }

    @AfterEach
    void cleanUp() {
        connector.close();
    }

    @Test
    void save() {
        var entity = new WindowBoundsEntity("hello", 100, 100, 800, 600, false);
        service.save(entity);

        var one = service.get(entity.identity());
        assertEquals(entity, one);
    }

    @Test
    void getDefault() {
        var entity = service.get("hello");
        assertEquals(WindowBoundsEntity.DEFAULT, entity);
    }

    @Test
    void delete() {
        var entity = new WindowBoundsEntity("hello", 100, 100, 800, 600, false);
        service.save(entity);

        var one = service.get(entity.identity());
        assertEquals(entity, one);

        service.delete(entity.identity());
        var def = service.get(entity.identity());
        assertEquals(WindowBoundsEntity.DEFAULT, def);
    }

    @Test
    void clear() {
        var entity = new WindowBoundsEntity("hello", 100, 100, 800, 600, false);
        var entity2 = new WindowBoundsEntity("second", 100, 100, 800, 600, false);
        service.save(entity);
        service.save(entity2);

        var one = service.get(entity.identity());
        assertEquals(entity, one);

        var two = service.get(entity2.identity());
        assertEquals(entity2, two);

        // clear
        service.clear();

        var def = service.get(entity.identity());
        assertEquals(WindowBoundsEntity.DEFAULT, def);

        def = service.get(entity2.identity());
        assertEquals(WindowBoundsEntity.DEFAULT, def);
    }
}