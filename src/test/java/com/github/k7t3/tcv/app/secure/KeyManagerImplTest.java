package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class KeyManagerImplTest {

    private Path storePath;
    private String password;
    private KeyManagerImpl manager;

    @BeforeEach
    void setUp() throws IOException {
        storePath = Files.createTempFile(null, null);
        Files.delete(storePath); // ファイルの実態はあって欲しくないので削除
        password = "changeit";
        manager = new KeyManagerImpl(storePath, password.toCharArray());
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.close();
        Files.deleteIfExists(storePath);
    }

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
    void flushTest() throws Exception {
        var generator = KeyGenerator.getInstance("AES");
        generator.init(256);

        var alias = "alias";
        var secret = generator.generateKey();
        manager.store(alias, secret);

        // 永続化
        manager.flush();
        manager.close();

        // 再度ロードして取り出す
        try (var manager = new KeyManagerImpl(storePath, password.toCharArray())) {
            var got = manager.getSecret(alias);
            assertEquals(secret, got);
        }
    }
}