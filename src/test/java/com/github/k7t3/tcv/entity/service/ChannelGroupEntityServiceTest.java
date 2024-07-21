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
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelGroupEntityServiceTest {

    private DBConnector connector;

    private ChannelGroupEntityService service;

    @BeforeEach
    void setUp() {
        connector = new TestDBConnector();
        connector.connect();
        service = new ChannelGroupEntityService(connector);
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
                "comment",
                false,
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
                "comment",
                false,
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
                "comment",
                false,
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