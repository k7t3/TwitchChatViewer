package com.github.k7t3.tcv.entity;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.service.ChannelGroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelGroupServiceTest {

    private DBConnector connector;

    private ChannelGroupService service;

    @BeforeEach
    void setUp() {
        connector = new TestDBConnector();
        connector.connect();
        service = new ChannelGroupService(connector);
    }

    @AfterEach
    void cleanUp() {
        connector.close();
    }

    @Test
    void save() {
        // SQLiteのタイムスタンプはミリ秒まで
        var inserted = new ChannelGroupEntity(
                UUID.randomUUID(),
                "test entity",
                LocalDateTime.now().withYear(1900).truncatedTo(ChronoUnit.MILLIS),
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                Set.of("first")
        );
        service.save(SaveType.INSERT, inserted);

        var all = service.retrieveAll();
        assertTrue(all.contains(inserted));

        var updated = new ChannelGroupEntity(
                inserted.id(),
                "name updated",
                inserted.createdAt(),
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                Set.of("second", "first")
        );
        service.save(SaveType.UPDATE, updated);

        all = service.retrieveAll();
        assertTrue(all.contains(updated));
        assertFalse(all.contains(inserted));
    }

    @Test
    void remove() {
        var inserted = new ChannelGroupEntity(
                UUID.randomUUID(),
                "test entity",
                LocalDateTime.now().withYear(1900).truncatedTo(ChronoUnit.MILLIS),
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                Set.of("first")
        );
        service.save(SaveType.INSERT, inserted);

        var all = service.retrieveAll();
        assertTrue(all.contains(inserted));

        service.remove(inserted);
        assertTrue(service.retrieveAll().isEmpty());
    }
}