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