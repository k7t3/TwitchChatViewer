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
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatKeywordFilterEntityServiceTest {

    private DBConnector connector;
    private ChatKeywordFilterEntityService service;

    @BeforeEach
    void setUp() {
        connector = new TestDBConnector();
        service = new ChatKeywordFilterEntityService(connector);
        connector.connect();
    }

    @AfterEach
    void tearDown() {
        connector.close();
    }

    @Test
    void insert() {
        var entity = new ChatKeywordFilterEntity(-1, 2, "keyword");
        var key = service.insert(entity);
        assertEquals(1, key);
        key = service.insert(entity);
        assertEquals(2, key); // autoincrement

        var list = service.retrieveAll();
        assertEquals(2, list.size());

        var first = list.getFirst();
        assertEquals(1, first.id());
        assertEquals(2, first.type());
        assertEquals("keyword", first.keyword());
    }

    @Test
    void update() {
        var entity = new ChatKeywordFilterEntity(-1, 2, "keyword");
        var key = service.insert(entity);
        assertEquals(1, key);

        var updated = new ChatKeywordFilterEntity(1, 2, "this is a name");
        service.update(updated);

        var list = service.retrieveAll();
        var one = list.getFirst();

        assertEquals(updated.keyword(), one.keyword());
    }

    @Test
    void delete() {
        var entity = new ChatKeywordFilterEntity(-1, 2, "keyword");
        var key = service.insert(entity);
        assertEquals(1, key);

        var remove = new ChatKeywordFilterEntity(1, 2, "keyword");
        service.delete(remove);

        assertTrue(service.retrieveAll().isEmpty());
    }
}