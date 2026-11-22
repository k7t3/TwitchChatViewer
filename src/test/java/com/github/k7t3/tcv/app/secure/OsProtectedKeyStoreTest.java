package com.github.k7t3.tcv.app.secure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.security.Security;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OsProtectedKeyStoreTest {
    @TempDir Path directory;

    @Test
    void migratingARestoredLegacyBackupDoesNotOverwriteTheNewOsSecret() throws Exception {
        var path = directory.resolve("keys.ks");
        var key = new SecretKeySpec(new byte[32], "AES");
        try (var legacy = new KeyManagerImpl(path, "changeit".toCharArray())) { legacy.store("credential", key); }
        var backup = Files.readAllBytes(path);
        var memory = new MemorySecrets();
        var secrets = new OsSecretStore() {
            @Override public byte[] read(String id) { return memory.read(id); }
            @Override public void save(String id, byte[] secret) {
                if (memory.values.containsKey(id)) throw new IllegalStateException("OS item already exists");
                memory.save(id, secret);
            }
        };

        try (var manager = new KeyManagerImpl(path, secrets)) {
            Files.write(path, backup);
            manager.store("additional", new SecretKeySpec(new byte[]{5, 6, 7, 8}, "HmacSHA256"));
        }

        try (var reader = new KeyManagerImpl(path, secrets)) {
            assertThat(reader.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
            assertThat(reader.getSecret("additional").getEncoded()).containsExactly(5, 6, 7, 8);
        }
        assertThat(memory.saveCount).isEqualTo(2);
    }

    @Test
    void aFailureBeforeReplacementKeepsTheLegacyStoreAndAllowsRetry() throws Exception {
        var path = directory.resolve("keys.ks");
        var key = new SecretKeySpec(new byte[32], "AES");
        try (var legacy = new KeyManagerImpl(path, "changeit".toCharArray())) { legacy.store("credential", key); }
        var before = Files.readAllBytes(path);
        var secrets = new MemorySecrets();
        var failure = new IOException("replacement failed");
        var files = new AtomicKeyStoreFile(AtomicKeyStoreFile::writeAndForce, (temporary, target) -> {
            assertThat(secrets.values).hasSize(1);
            throw failure;
        });

        assertThatThrownBy(() -> {
            try (var ignored = KeyStoreSession.osProtected(path, secrets, files)) { }
        }).isSameAs(failure);

        assertThat(Files.readAllBytes(path)).containsExactly(before);
        try (var legacy = new KeyManagerImpl(path, "changeit".toCharArray())) {
            assertThat(legacy.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
        }
        try (var retry = new KeyManagerImpl(path, secrets)) {
            assertThat(retry.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
        }
        try (var entries = Files.list(directory)) {
            assertThat(entries.map(value -> value.getFileName().toString())).noneMatch(name -> name.startsWith(".tcv-keys-"));
        }
    }

    @Test
    void aFailureReportedAfterReplacementKeepsThePublishedStoresSecretReadable() throws Exception {
        var path = directory.resolve("keys.ks");
        var key = new SecretKeySpec(new byte[32], "AES");
        try (var legacy = new KeyManagerImpl(path, "changeit".toCharArray())) { legacy.store("credential", key); }
        var secrets = new MemorySecrets();
        var failure = new IOException("replacement completed but confirmation failed");
        var files = new AtomicKeyStoreFile(AtomicKeyStoreFile::writeAndForce, (temporary, target) -> {
            AtomicKeyStoreFile.replace(temporary, target);
            throw failure;
        });

        assertThatThrownBy(() -> {
            try (var ignored = KeyStoreSession.osProtected(path, secrets, files)) { }
        }).isSameAs(failure);

        assertThat(Files.readAllBytes(path)).startsWith(new byte[]{'T', 'C', 'V', 'K', 'E', 'Y', 'S', 2});
        assertThat(secrets.values).hasSize(1);
        try (var reader = new KeyManagerImpl(path, secrets)) {
            assertThat(reader.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"truncated", "future", "tampered"})
    void aDamagedProtectedStoreIsNeverReplacedWithAnEmptyStore(String reason) throws Exception {
        var path = directory.resolve("keys.ks");
        var secrets = new MemorySecrets();
        try (var writer = new KeyManagerImpl(path, secrets)) {
            writer.store("credential", new SecretKeySpec(new byte[32], "AES"));
        }
        var damaged = Files.readAllBytes(path);
        if (reason.equals("truncated")) damaged = Arrays.copyOf(damaged, 12);
        else if (reason.equals("future")) damaged[7] = 99;
        else damaged[damaged.length - 1] ^= 1;
        Files.write(path, damaged);
        secrets.readCount = 0;

        assertThatThrownBy(() -> new KeyManagerImpl(path, secrets)).isInstanceOf(KeyManagerException.class);

        assertThat(Files.readAllBytes(path)).containsExactly(damaged);
        assertThat(secrets.saveCount).isEqualTo(1);
        assertThat(secrets.readCount).isEqualTo(reason.equals("tampered") ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong", "failure"})
    void anUnavailableOsSecretCannotReplaceTheProtectedFile(String reason) throws Exception {
        var path = directory.resolve("keys.ks");
        var secrets = new MemorySecrets();
        try (var writer = new KeyManagerImpl(path, secrets)) {
            writer.store("credential", new SecretKeySpec(new byte[32], "AES"));
        }
        var before = Files.readAllBytes(path);
        var failure = new IllegalStateException("OS access denied");
        if (reason.equals("missing")) secrets.values.clear();
        else if (reason.equals("wrong")) secrets.values.values().forEach(value -> Arrays.fill(value, (byte) 7));
        else if (reason.equals("failure")) secrets.readFailure = failure;
        else throw new AssertionError(reason);

        var thrown = assertThatThrownBy(() -> new KeyManagerImpl(path, secrets));
        if (reason.equals("failure")) thrown.isSameAs(failure);
        else thrown.isInstanceOf(KeyManagerException.class);

        assertThat(Files.readAllBytes(path)).containsExactly(before);
        assertThat(secrets.saveCount).isEqualTo(1);
    }

    @Test
    void failingToSaveTheOsSecretLeavesAllLegacyKeyBytesReadable() throws Exception {
        var path = directory.resolve("keys.ks");
        var key = new SecretKeySpec(new byte[32], "AES");
        try (var legacy = new KeyManagerImpl(path, "changeit".toCharArray())) { legacy.store("credential", key); }
        var before = Files.readAllBytes(path);
        var secrets = new MemorySecrets();
        var failure = new IllegalStateException("OS save denied");
        secrets.saveFailure = failure;

        assertThatThrownBy(() -> new KeyManagerImpl(path, secrets)).isSameAs(failure);

        assertThat(Files.readAllBytes(path)).containsExactly(before);
        assertThat(secrets.values).isEmpty();
        try (var legacy = new KeyManagerImpl(path, "changeit".toCharArray())) {
            assertThat(legacy.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
        }
    }

    @Test
    void aMissingDecryptionKeyDoesNotCreateAnOsSecretOrAKeyFile() {
        var path = directory.resolve("missing.ks");
        var secrets = new MemorySecrets();

        try (var manager = new KeyManagerImpl(path, secrets)) {
            assertThat(manager.getSecret("credential")).isNull();
        }

        assertThat(path).doesNotExist();
        assertThat(secrets.values).isEmpty();
        assertThat(secrets.readCount).isZero();
        assertThat(secrets.saveCount).isZero();
    }

    @Test
    void theEmbeddedPayloadCannotBeOpenedWithTheHistoricalFixedPassword() throws Exception {
        var path = directory.resolve("keys.ks");
        var secrets = new MemorySecrets();
        try (var writer = new KeyManagerImpl(path, secrets)) {
            writer.store("credential", new SecretKeySpec(new byte[32], "AES"));
        }
        var bytes = Files.readAllBytes(path);
        var payload = KeyStore.getInstance("PKCS12");

        try (var input = new ByteArrayInputStream(bytes, 24, bytes.length - 24)) {
            assertThatThrownBy(() -> payload.load(input, "changeit".toCharArray())).isInstanceOf(IOException.class);
        }
    }

    @Test
    void secretBuffersBorrowedFromTheOsStoreAreClearedAfterEachOperation() {
        var path = directory.resolve("keys.ks");
        var secrets = new MemorySecrets();
        try (var writer = new KeyManagerImpl(path, secrets)) {
            writer.store("credential", new SecretKeySpec(new byte[32], "AES"));
            writer.getSecret("credential");
        }

        assertThat(secrets.borrowed).hasSize(2).allSatisfy(value -> assertThat(value).containsOnly((byte) 0));
        assertThat(secrets.values.values()).singleElement().satisfies(value -> assertThat(value).hasSize(32));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PKCS12", "JCEKS"})
    void migrationPreservesOldAndCurrentCredentialsAndOtherKeys(String type) throws Exception {
        var path = directory.resolve("keys.ks");
        var legacyKey = new byte[32];
        for (var i = 0; i < legacyKey.length; i++) legacyKey[i] = (byte) i;
        var store = KeyStore.getInstance(type);
        store.load(null, "changeit".toCharArray());
        var protection = new KeyStore.PasswordProtection("changeit".toCharArray());
        try {
            store.setEntry("c.o", new KeyStore.SecretKeyEntry(new SecretKeySpec(legacyKey, "AES")), protection);
            store.setEntry("credential.gcm.v2", new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "AES")), protection);
            store.setEntry("another-key", new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[]{1, 2, 3, 4}, "HmacSHA256")), protection);
        } finally { protection.destroy(); }
        try (var output = Files.newOutputStream(path)) { store.store(output, "changeit".toCharArray()); }
        var plain = "保存済みの資格情報".getBytes(StandardCharsets.UTF_8);
        byte[] current;
        try (var old = new KeyManagerImpl(path, "changeit".toCharArray())) {
            current = new CredentialCipher(old).encrypt(plain);
        }
        var legacy = Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0OD/0kOtZU2ziRoQja5A5AWlPtei0zpnYiAkqB+O9yE/2x");
        var secrets = new MemorySecrets();

        try (var migrated = new KeyManagerImpl(path, secrets)) {
            var cipher = new CredentialCipher(migrated);
            assertThat(cipher.decrypt(current)).containsExactly(plain);
            assertThat(new String(cipher.decrypt(legacy), StandardCharsets.UTF_8)).isEqualTo("legacy credential fixture");
            assertThat(migrated.getSecret("another-key").getEncoded()).containsExactly(1, 2, 3, 4);
        }

        assertThat(Files.readAllBytes(path)).startsWith(new byte[]{'T', 'C', 'V', 'K', 'E', 'Y', 'S', 2});
        assertThat(secrets.values).hasSize(1);
    }

    @Test
    void aProtectedStoreCanBeReadAfterTheDefaultKeystoreTypeChanges() {
        var path = directory.resolve("keys.ks");
        var secrets = new MemorySecrets();
        var key = new SecretKeySpec(new byte[32], "AES");
        try (var writer = new KeyManagerImpl(path, secrets)) { writer.store("credential", key); }
        var previous = Security.getProperty("keystore.type");
        try {
            Security.setProperty("keystore.type", "JCEKS");

            try (var reader = new KeyManagerImpl(path, secrets)) {
                assertThat(reader.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
            }
        } finally { Security.setProperty("keystore.type", previous); }
    }

    @Test
    void keysCanBeReopenedUsingTheOsSecretWithoutAFilePassword() throws Exception {
        var path = directory.resolve("keys.ks");
        var secrets = new MemorySecrets();
        var key = new SecretKeySpec(new byte[32], "AES");

        try (var writer = new KeyManagerImpl(path, secrets)) {
            writer.store("credential", key);
        }

        assertThat(Files.readAllBytes(path)).startsWith(new byte[]{'T', 'C', 'V', 'K', 'E', 'Y', 'S', 2});
        assertThat(secrets.values).hasSize(1);
        try (var reader = new KeyManagerImpl(path, secrets)) {
            assertThat(reader.getSecret("credential").getEncoded()).containsExactly(key.getEncoded());
        }
    }

    private static final class MemorySecrets implements OsSecretStore {
        final Map<String, byte[]> values = new HashMap<>();
        final ArrayList<byte[]> borrowed = new ArrayList<>();
        RuntimeException readFailure;
        RuntimeException saveFailure;
        int readCount;
        int saveCount;

        @Override public byte[] read(String id) {
            readCount++;
            if (readFailure != null) throw readFailure;
            var value = values.get(id);
            if (value == null) return null;
            var result = value.clone(); borrowed.add(result); return result;
        }

        @Override public void save(String id, byte[] secret) {
            saveCount++;
            if (saveFailure != null) throw saveFailure;
            borrowed.add(secret);
            values.put(id, secret.clone());
        }
    }
}
