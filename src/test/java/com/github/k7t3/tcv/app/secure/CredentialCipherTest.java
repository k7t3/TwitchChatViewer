package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.AEADBadTagException;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {
    @TempDir Path directory;
    private final char[] password = "test-password".toCharArray();
    private static final byte[] PLAIN = "credential bytes".getBytes(StandardCharsets.UTF_8);
    // 既存の IV(16) + AES/CBC/PKCS5Padding。鍵は 00..1f、IV は 00..0f。
    private static final byte[] LEGACY = Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0OD/0kOtZU2ziRoQja5A5AWlPtei0zpnYiAkqB+O9yE/2x");

    @ParameterizedTest
    @ValueSource(strings = {"", "credential bytes"})
    void aFreshReaderCanDecryptBeforeTheEncryptingManagerCloses(String text) {
        var path = directory.resolve("keys.p12");
        try (var manager = new KeyManagerImpl(path, password)) {
            var cipher = new CredentialCipher(manager);
            var bytes = text.getBytes(StandardCharsets.UTF_8);
            var first = cipher.encrypt(bytes);
            var second = cipher.encrypt(bytes);
            assertThat(first).startsWith(new byte[]{'T', 'C', 'V', 'C', 'R', 'E', 'D', 2});
            assertThat(first).isNotEqualTo(second);
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(new CredentialCipher(reader).decrypt(first)).containsExactly(bytes);
                assertThat(new CredentialCipher(reader).decrypt(second)).containsExactly(bytes);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 20, 51})
    void changesToTheNonceCiphertextOrTagAreRejected(int offset) {
        try (var manager = new KeyManagerImpl(directory.resolve("keys.p12"), password)) {
            var cipher = new CredentialCipher(manager);
            var encrypted = cipher.encrypt(PLAIN);
            encrypted[offset] ^= 1;
            assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(KeyManagerException.class)
                    .hasCauseInstanceOf(AEADBadTagException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void anUnsupportedOrDamagedHeaderIsRejectedWithoutChangingTheKeyStore(int offset) throws Exception {
        var path = directory.resolve("keys.p12");
        try (var manager = new KeyManagerImpl(path, password)) {
            manager.store("c.o", legacyKey());
            var cipher = new CredentialCipher(manager);
            var encrypted = cipher.encrypt(PLAIN);
            var before = Files.readAllBytes(path);
            encrypted[offset] ^= 1;
            assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(KeyManagerException.class);
            assertThat(Files.readAllBytes(path)).containsExactly(before);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 8, 19, 20, 35, 51})
    void aTruncatedEnvelopeDoesNotReturnPartialPlaintext(int length) {
        try (var manager = new KeyManagerImpl(directory.resolve("keys.p12"), password)) {
            var cipher = new CredentialCipher(manager);
            var encrypted = cipher.encrypt(PLAIN);
            assertThatThrownBy(() -> cipher.decrypt(Arrays.copyOf(encrypted, length))).isInstanceOf(KeyManagerException.class);
        }
    }

    @Test
    void legacyCredentialsAreReadWithoutMutationAndTheNextWriteUsesTheAuthenticatedFormat() throws Exception {
        var path = directory.resolve("keys.p12");
        try (var manager = new KeyManagerImpl(path, password)) {
            manager.store("c.o", legacyKey());
            var before = Files.readAllBytes(path);
            var cipher = new CredentialCipher(manager);
            var restored = cipher.decrypt(LEGACY);
            assertThat(new String(restored, StandardCharsets.UTF_8)).isEqualTo("legacy credential fixture");
            assertThat(Files.readAllBytes(path)).containsExactly(before);
            var current = cipher.encrypt(restored);
            assertThat(current).startsWith(new byte[]{'T', 'C', 'V', 'C', 'R', 'E', 'D', 2});
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(reader.getSecret("c.o")).isEqualTo(legacyKey());
                assertThat(new CredentialCipher(reader).decrypt(current)).containsExactly(restored);
                assertThat(new CredentialCipher(reader).decrypt(LEGACY)).containsExactly(restored);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void decryptingWithoutTheRequiredKeyDoesNotGenerateAReplacement(boolean legacy) throws Exception {
        byte[] encrypted;
        try (var writer = new KeyManagerImpl(directory.resolve("source.p12"), password)) {
            encrypted = legacy ? LEGACY : new CredentialCipher(writer).encrypt(PLAIN);
        }
        var missing = directory.resolve("missing.p12");
        try (var reader = new KeyManagerImpl(missing, password)) {
            assertThatThrownBy(() -> new CredentialCipher(reader).decrypt(encrypted)).isInstanceOf(KeyManagerException.class)
                    .hasMessageContaining("missing");
            assertThat(Files.exists(missing)).isFalse();
        }
    }

    @Test
    void aFailedKeyCommitCannotPublishCiphertextOrCacheAnUnpersistedKey() throws Exception {
        var path = directory.resolve("keys.p12");
        var fail = new AtomicBoolean(true);
        var failure = new IOException("cannot commit key");
        var files = new AtomicKeyStoreFile(AtomicKeyStoreFile::writeAndForce, (temporary, target) -> {
            if (fail.get()) throw failure;
            AtomicKeyStoreFile.replace(temporary, target);
        });
        try (var manager = new KeyManagerImpl(path, password, files)) {
            var cipher = new CredentialCipher(manager);
            assertThatThrownBy(() -> cipher.encrypt(PLAIN)).isInstanceOf(KeyManagerException.class).hasCause(failure);
            assertThat(Files.exists(path)).isFalse();
            fail.set(false);
            var encrypted = cipher.encrypt(PLAIN);
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(new CredentialCipher(reader).decrypt(encrypted)).containsExactly(PLAIN);
            }
        }
    }

    @Test
    void aClosedManagerCannotBeBypassedByACachedCipherKey() {
        var manager = new KeyManagerImpl(directory.resolve("keys.p12"), password);
        var cipher = new CredentialCipher(manager);
        var encrypted = cipher.encrypt(PLAIN);
        manager.close();
        assertThatThrownBy(() -> cipher.encrypt(PLAIN)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    private static SecretKeySpec legacyKey() {
        var bytes = new byte[32];
        for (var index = 0; index < bytes.length; index++) bytes[index] = (byte) index;
        return new SecretKeySpec(bytes, "AES");
    }
}
