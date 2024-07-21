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