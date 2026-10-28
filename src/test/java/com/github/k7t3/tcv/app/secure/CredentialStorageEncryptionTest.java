package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialStorageEncryptionTest {
    @TempDir Path directory;
    private final char[] password = "test-password".toCharArray();

    @Test
    void theRealStorageFacadeAndCipherSurviveAFreshManagerBeforeTheWriterCloses() throws Exception {
        var path = directory.resolve("keys.p12");
        var preferences = new MemoryPreferences();
        var old = credential("original");
        try (var first = new KeyManagerImpl(path, password)) {
            var store = storage(preferences, first);
            store.saveCredentials(List.of(old));
            var committedKeys = Files.readAllBytes(path);
            try (var reader = new KeyManagerImpl(path, password)) {
                var reopened = storage(preferences, reader);
                assertThat(reopened.loadCredentials()).containsExactly(old);
                var previous = reopened.snapshot();
                assertThat(reopened.compareAndSet(previous, List.of(credential("refreshed")), () -> true)).isPresent();
                assertThat(store.loadCredentials()).containsExactly(credential("refreshed"));
                var current = reopened.snapshot();
                assertThat(reopened.compareAndSet(current, List.of(), () -> true)).isPresent();
                assertThat(reopened.loadCredentials()).isEmpty();
                assertThat(reopened.compareAndSet(previous, List.of(old), () -> true)).isEmpty();
                assertThat(Files.readAllBytes(path)).containsExactly(committedKeys);
            }
        }
    }

    @Test
    void aFailedNewKeyCommitPreservesTheLegacyCiphertextAndItsDecryptionKey() throws Exception {
        var path = directory.resolve("keys.p12");
        var key = new SecretKeySpec(new byte[32], "AES");
        try (var initial = new KeyManagerImpl(path, password)) { initial.store("c.o", key); }
        var before = Files.readAllBytes(path);
        // 既存の資格情報バイナリを旧 CBC で保存した fixture。
        var binary = Base64.getDecoder().decode("AAZ0d2l0Y2gABmxlZ2FjeQAETlVMTAADMTIzAARVc2VyAAAOEAAAAAEACWNoYXQ6cmVhZAAAAAEAB2tleT1hPWIAAAGHJ82gAA==");
        var legacyCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        legacyCipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[16]));
        var encrypted = legacyCipher.doFinal(binary);
        var original = new byte[16 + encrypted.length];
        System.arraycopy(encrypted, 0, original, 16, encrypted.length);
        var preferences = new MemoryPreferences();
        preferences.putByteArray("credentials", original);
        var failure = new IOException("cannot commit new key");
        var files = new AtomicKeyStoreFile(AtomicKeyStoreFile::writeAndForce, (temporary, target) -> { throw failure; });
        try (var manager = new KeyManagerImpl(path, password, files)) {
            var store = storage(preferences, manager);
            assertThatThrownBy(() -> store.saveCredentials(List.of(credential("new"))))
                    .isInstanceOf(KeyManagerException.class).hasCause(failure);
            assertThat(preferences.getByteArray("credentials", null)).containsExactly(original);
            assertThat(Files.readAllBytes(path)).containsExactly(before);
            assertThat(((OAuth2Credential) store.loadCredentials().getFirst()).getAccessToken()).isEqualTo("legacy");
        }
    }

    @Test
    void aChangedAuthenticationTagCannotBeReadOrReplaceStoredCredentials() {
        var preferences = new MemoryPreferences();
        try (var manager = new KeyManagerImpl(directory.resolve("keys.p12"), password)) {
            var store = storage(preferences, manager);
            store.saveCredentials(List.of(credential("original")));
            var corrupt = preferences.getByteArray("credentials", null);
            corrupt[corrupt.length - 1] ^= 1;
            preferences.putByteArray("credentials", corrupt);
            assertThatThrownBy(store::snapshot).isInstanceOf(KeyManagerException.class);
            assertThat(preferences.getByteArray("credentials", null)).containsExactly(corrupt);
        }
    }

    private static PreferencesCredentialStorage storage(java.util.prefs.Preferences preferences, KeyManager manager) {
        var cipher = new CredentialCipher(manager);
        return new PreferencesCredentialStorage(preferences, cipher::encrypt, cipher::decrypt);
    }

    private static OAuth2Credential credential(String token) {
        var value = new OAuth2Credential("twitch", token, "refresh-" + token, "123", "User", 3600,
                List.of("chat:read"), Map.of("test", "value"));
        value.setReceivedAt(Instant.ofEpochMilli(1680000000000L));
        return value;
    }
}
