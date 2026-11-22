package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferencesCredentialStorageTest {
    @Test
    void deletingTheLastSessionDoesNotRestoreTheRevisionOfAnEarlierEmptyStore() {
        var preferences = new MemoryPreferences();
        var store = storage(preferences);
        var beforeLogin = store.snapshot();
        var loggedIn = store.compareAndSet(beforeLogin, List.of(credential("current")), () -> true).orElseThrow();
        store.compareAndSet(loggedIn, List.of(), () -> true).orElseThrow();

        assertThat(store.loadCredentials()).isEmpty();
        assertThat(store.compareAndSet(beforeLogin, List.of(credential("late-authentication")), () -> true)).isEmpty();
    }

    @Test
    void anOldSnapshotCannotOverwriteANewerCredentialEvenWhenTheAccessTokenIsTheSame() {
        var preferences = new MemoryPreferences();
        var store = storage(preferences);
        var previous = credential("same-token");
        store.saveCredentials(List.of(previous));
        var snapshot = store.snapshot();
        var current = credential("same-token");
        current.setRefreshToken("new-refresh-token");
        storage(preferences).saveCredentials(List.of(current));

        assertThat(store.compareAndSet(snapshot, List.of(credential("stale-refresh")), () -> true)).isEmpty();

        assertThat(store.loadCredentials()).containsExactly(current);
    }

    @Test
    void aCancelledCommitCannotChangeStoredDataOrItsRevision() {
        var preferences = new MemoryPreferences();
        var store = storage(preferences);
        store.saveCredentials(List.of(credential("previous")));
        var snapshot = store.snapshot();

        assertThat(store.compareAndSet(snapshot, List.of(credential("new")), () -> false)).isEmpty();

        assertThat(store.snapshot().revision()).isEqualTo(snapshot.revision());
        assertThat(store.loadCredentials()).containsExactly(credential("previous"));
    }

    @Test
    void aSuccessfulCommitReturnsTheRevisionThatWillBeComparedByTheNextWriter() {
        var preferences = new MemoryPreferences();
        var store = storage(preferences);
        var empty = store.snapshot();

        var saved = store.compareAndSet(empty, List.of(credential("first")), () -> true).orElseThrow();
        assertThat(store.snapshot().revision()).isEqualTo(saved.revision());
        assertThat(store.compareAndSet(empty, List.of(credential("stale")), () -> true)).isEmpty();
        assertThat(store.compareAndSet(saved, List.of(credential("second")), () -> true)).isPresent();
        assertThat(store.loadCredentials()).containsExactly(credential("second"));
    }

    // 既存の DataOutputStream 形式。NULL sentinel、context 内の '='、ミリ秒の受信時刻を含む。
    private static final String LEGACY = "AAZ0d2l0Y2gABmxlZ2FjeQAETlVMTAADMTIzAARVc2VyAAAOEAAAAAEACWNoYXQ6cmVhZAAAAAEAB2tleT1hPWIAAAGHJ82gAA==";

    @Test
    void legacyBytesCanBeReadAndWrittenWithoutAFormatChange() {
        var preferences = new MemoryPreferences();
        var bytes = Base64.getDecoder().decode(LEGACY);
        preferences.putByteArray("credentials", bytes);
        var expected = new OAuth2Credential("twitch", "legacy", null, "123", "User", 3600,
                List.of("chat:read"), Map.of("key", "a=b"));
        expected.setReceivedAt(Instant.ofEpochMilli(1680000000000L));
        var storage = storage(preferences);

        assertThat(storage.loadCredentials()).containsExactly(expected);
        storage.saveCredentials(List.of(expected));

        assertThat(preferences.getByteArray("credentials", null)).containsExactly(bytes);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MAX_VALUE})
    void invalidEntryCountsAreRejectedWithoutChangingTheStoredBytes(int count) {
        var preferences = new MemoryPreferences();
        var bytes = Base64.getDecoder().decode(LEGACY);
        ByteBuffer.wrap(bytes).putInt(37, count);
        preferences.putByteArray("credentials", bytes);

        assertThatThrownBy(() -> storage(preferences).loadCredentials()).isInstanceOf(UncheckedIOException.class);

        assertThat(preferences.getByteArray("credentials", null)).containsExactly(bytes);
    }

    @Test
    void encryptionFailurePreservesThePreviouslyStoredCredential() {
        var preferences = new MemoryPreferences();
        var fail = new AtomicBoolean();
        var failure = new IllegalStateException("encryption failed");
        var storage = new PreferencesCredentialStorage(preferences, bytes -> {
            if (fail.get()) throw failure;
            return bytes.clone();
        }, byte[]::clone);
        var previous = credential("previous");
        storage.saveCredentials(List.of(previous));
        var bytes = preferences.getByteArray("credentials", null);
        fail.set(true);

        assertThatThrownBy(() -> storage.saveCredentials(List.of(credential("new")))).isSameAs(failure);

        assertThat(preferences.getByteArray("credentials", null)).containsExactly(bytes);
        assertThat(storage.loadCredentials()).containsExactly(previous);
    }

    @Test
    void comparisonAndRemovalCompleteBeforeAnotherInstanceCanSave() throws Exception {
        var preferences = new MemoryPreferences();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var previous = credential("previous");
        var current = credential("current");
        var removing = new PreferencesCredentialStorage(preferences, byte[]::clone, bytes -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("credential read was not released");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
            return bytes.clone();
        });
        removing.saveCredentials(List.of(previous));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var removal = workers.submit(() -> removing.removeCredential(previous));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var saving = workers.submit(() -> storage(preferences).saveCredentials(List.of(current)));
                assertThatThrownBy(() -> saving.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(removal.get(5, TimeUnit.SECONDS)).isTrue();
                saving.get(5, TimeUnit.SECONDS);
                assertThat(storage(preferences).loadCredentials()).containsExactly(current);
            } finally {
                release.countDown();
                removal.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void oldStoreInstancesCannotDeleteANewerCredentialSavedByAnotherInstance() {
        var preferences = new MemoryPreferences();
        var previous = storage(preferences);
        var current = storage(preferences);
        var oldCredential = credential("old");
        var newCredential = credential("new");
        previous.saveCredentials(List.of(oldCredential));
        previous.loadCredentials();
        current.saveCredentials(List.of(newCredential));

        assertThat(previous.removeCredential(oldCredential)).isFalse();

        assertThat(current.loadCredentials()).containsExactly(newCredential);
        assertThat(previous.loadCredentials()).containsExactly(newCredential);
    }

    @Test
    void removingOneCredentialPreservesOtherAccountsAndTheExistingFormat() {
        var preferences = new MemoryPreferences();
        var storage = storage(preferences);
        var first = credential("first");
        var second = credential("second");
        storage.saveCredentials(List.of(first, second));

        assertThat(storage.removeCredential(first)).isTrue();

        assertThat(storage(preferences).loadCredentials()).containsExactly(second);
        assertThat(storage.removeCredential(second)).isTrue();
        assertThat(preferences.getByteArray("credentials", null)).isNull();
    }

    @Test
    void corruptStoredDataCannotBePartiallyReadAndThenDeleted() {
        var preferences = new MemoryPreferences();
        var storage = storage(preferences);
        var first = credential("first");
        storage.saveCredentials(List.of(first, credential("second")));
        var bytes = preferences.getByteArray("credentials", null);
        var truncated = Arrays.copyOf(bytes, bytes.length - 1);
        preferences.putByteArray("credentials", truncated);

        assertThatThrownBy(() -> storage.removeCredential(first)).isInstanceOf(UncheckedIOException.class);

        assertThat(preferences.getByteArray("credentials", null)).containsExactly(truncated);
    }

    @Test
    void loadingReturnsDetachedCredentialObjectsAndLookupReadsTheCurrentStorage() {
        var preferences = new MemoryPreferences();
        var storage = storage(preferences);
        var credential = credential("first");
        storage.saveCredentials(List.of(credential));
        var loaded = (OAuth2Credential) storage.loadCredentials().getFirst();
        loaded.setAccessToken("local mutation");

        assertThat(storage.loadCredentials()).containsExactly(credential);
        assertThat(storage(preferences).getCredentialByUserId("123")).contains(credential);
    }

    private static PreferencesCredentialStorage storage(MemoryPreferences preferences) {
        return new PreferencesCredentialStorage(preferences, byte[]::clone, byte[]::clone);
    }

    private static OAuth2Credential credential(String token) {
        var credential = new OAuth2Credential("twitch", token, "refresh-" + token, "123", "User", 3600,
                List.of("chat:read"), Map.of("test", "value"));
        credential.setReceivedAt(Instant.ofEpochMilli(1680000000000L));
        return credential;
    }

}
