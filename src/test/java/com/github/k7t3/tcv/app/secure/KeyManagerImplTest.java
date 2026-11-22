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

package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.KeyGenerator;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class KeyManagerImplTest {

    @TempDir Path directory;
    private Path storePath;
    private String password;
    private KeyManagerImpl manager;

    @BeforeEach
    void setUp() {
        storePath = directory.resolve("keys.p12");
        password = "changeit";
        manager = new KeyManagerImpl(storePath, password.toCharArray());
    }

    @AfterEach
    void tearDown() { manager.close(); }

    @Test
    void getNullTest() {
        var alias = "alias";
        var secret = manager.getSecret(alias);
        assertNull(secret);
    }

    @Test
    void storeTest() throws NoSuchAlgorithmException {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);

        var alias = "alias";
        var secret = generator.generateKey();
        manager.store(alias, secret);

        var got = manager.getSecret(alias);
        assertEquals(secret, got);
    }

    @Test
    void aStoredSecretCanBeReopenedBeforeTheWriterCloses() throws Exception {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);

        var alias = "alias";
        var secret = generator.generateKey();
        manager.store(alias, secret);


        // 再度ロードして取り出す
        try (var manager = new KeyManagerImpl(storePath, password.toCharArray())) {
            var got = manager.getSecret(alias);
            assertEquals(secret, got);
        }
    }
}