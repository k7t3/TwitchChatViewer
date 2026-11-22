package com.github.k7t3.tcv.app.secure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class KeyManagerFactoryTest {
    @TempDir Path directory;

    @Test
    void closingAnUnusedFactoryServiceDoesNotConstructOrTouchStorage() {
        var path = directory.resolve("unused.ks");
        var created = new AtomicInteger();

        try (var service = KeyManagerFactory.create(path, null, () -> {
            created.incrementAndGet();
            throw new AssertionError("unused service must not construct OS storage");
        })) {}

        assertThat(created).hasValue(0);
        assertThat(path).doesNotExist();
    }

    @Test
    void anExplicitPasswordReopensTheExistingStoreWithoutUsingOrMigratingOsStorage() throws Exception {
        var path = directory.resolve("explicit.ks");
        var password = "fixture-only-password";
        var plain = new byte[] {3, 4};
        byte[] encrypted;
        try (var previous = new CredentialCipherService(() -> new KeyManagerImpl(path, password.toCharArray()))) {
            encrypted = previous.encrypt(plain);
        }
        var original = Files.readAllBytes(path);

        try (var service = KeyManagerFactory.create(path, password, () -> {
            throw new AssertionError("explicit password must not open OS storage");
        })) {
            assertThat(service.decrypt(encrypted)).containsExactly(plain);
        }

        assertThat(Files.readAllBytes(path)).containsExactly(original);
    }

    @Test
    void noPasswordOverrideUsesAnOsProtectedKeyThatAnotherServiceCanReopen() throws Exception {
        var path = directory.resolve("keys.ks");
        var secrets = new HashMap<String, byte[]>();
        var store = new OsSecretStore() {
            @Override public byte[] read(String id) { return secrets.containsKey(id) ? secrets.get(id).clone() : null; }
            @Override public void save(String id, byte[] secret) { secrets.put(id, secret.clone()); }
        };
        var plain = new byte[] {7, 8, 9};
        byte[] encrypted;

        try (var service = KeyManagerFactory.create(path, null, () -> store)) { encrypted = service.encrypt(plain); }

        assertThat(Files.readAllBytes(path)).startsWith(new byte[] {'T', 'C', 'V', 'K', 'E', 'Y', 'S', 2});
        assertThat(secrets).hasSize(1);
        try (var reopened = KeyManagerFactory.create(path, null, () -> store)) {
            assertThat(reopened.decrypt(encrypted)).containsExactly(plain);
        }
    }
}
