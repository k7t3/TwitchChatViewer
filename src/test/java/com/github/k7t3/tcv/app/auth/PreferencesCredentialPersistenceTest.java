package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferencesCredentialPersistenceTest {
    @TempDir Path directory;

    @Test
    void aSnapshotRefreshesThePersistentRevisionBeforeItReturns() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var reader = storage(file, lock);
        var old = reader.snapshot();
        var changed = storage(file, lock).compareAndSet(old, List.of(credential("new")), () -> true).orElseThrow();

        var observed = reader.snapshot();

        assertThat(observed.revision()).isEqualTo(changed.revision());
        assertThat(observed.credentials()).containsExactly(credential("new"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"load", "by-user"})
    void everyCredentialReaderRefreshesAnOlderNode(String readerKind) throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var reader = storage(file, lock);
        storage(file, lock).compareAndSet(new CredentialStore.Snapshot("", List.of()),
                List.of(credential("new")), () -> true).orElseThrow();

        var observed = readerKind.equals("load") ? reader.loadCredentials()
                : reader.getCredentialByUserId("account").stream().toList();

        assertThat(observed).containsExactly(credential("new"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "remove", "clear"})
    void everyMutationPublishesItsResultBeforeItReturns(String mutation) throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var mutator = storage(file, lock);
        storage(file, lock).compareAndSet(new CredentialStore.Snapshot("", List.of()),
                List.of(credential("old")), () -> true).orElseThrow();

        switch (mutation) {
            case "save" -> mutator.saveCredentials(List.of(credential("new")));
            case "remove" -> assertThat(mutator.removeCredential(credential("old"))).isTrue();
            case "clear" -> mutator.clearCredentials();
            default -> throw new AssertionError("unknown mutation fixture");
        }

        var observed = storage(file, lock).loadCredentials();
        if (mutation.equals("save")) assertThat(observed).containsExactly(credential("new"));
        else assertThat(observed).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cas", "save", "remove", "clear"})
    void failedFlushRestoresThePreviousCiphertextBeforeReleasingTheLock(String mutation) throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var seed = storage(file, lock);
        seed.saveCredentials(List.of(credential("old")));
        var expected = seed.snapshot();
        var failure = new java.util.prefs.BackingStoreException("synthetic failure after publication");
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var node = new PreferencesCredentialProcessTest.FilePreferences(file) {
            @Override protected void flushSpi() throws java.util.prefs.BackingStoreException {
                super.flushSpi();
                if (failOnce.getAndSet(false)) throw failure;
            }
        };
        var before = node.getByteArray("credentials", null);
        var store = new PreferencesCredentialStorage(node, byte[]::clone, byte[]::clone, lock);

        assertThatThrownBy(() -> {
            switch (mutation) {
                case "cas" -> store.compareAndSet(expected, List.of(credential("new")), () -> true);
                case "save" -> store.saveCredentials(List.of(credential("new")));
                case "remove" -> store.removeCredential(credential("old"));
                case "clear" -> store.clearCredentials();
                default -> throw new AssertionError("unknown mutation fixture");
            }
        }).isInstanceOf(IllegalStateException.class).hasCause(failure);

        assertThat(node.getByteArray("credentials", null)).containsExactly(before);
        assertThat(storage(file, lock).loadCredentials()).containsExactly(credential("old"));
    }

    @Test
    void thePermissionCallbackCanInspectTheSameStoreWithoutReacquiringItsFileLock() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var store = storage(file, directory.resolve("credentials.lock"));
        var before = store.snapshot();
        var inspected = new java.util.concurrent.atomic.AtomicBoolean();

        var changed = store.compareAndSet(before, List.of(credential("new")), () -> {
            assertThat(store.snapshot()).isEqualTo(before);
            inspected.set(true);
            return true;
        });

        assertThat(inspected).isTrue();
        assertThat(changed).isPresent();
        assertThat(storage(file, directory.resolve("credentials.lock")).loadCredentials())
                .containsExactly(credential("new"));
    }

    @Test
    void failureToBuildTheReturnedSnapshotCannotPublishANewCredential() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var seed = storage(file, lock);
        seed.saveCredentials(List.of(credential("old")));
        var expected = seed.snapshot();
        var node = new PreferencesCredentialProcessTest.FilePreferences(file);
        var before = node.getByteArray("credentials", null);
        var failure = new IllegalStateException("synthetic decryption failure");
        var store = new PreferencesCredentialStorage(node, byte[]::clone, bytes -> {
            if (!java.util.Arrays.equals(bytes, before)) throw failure;
            return bytes.clone();
        }, lock);

        assertThatThrownBy(() -> store.compareAndSet(expected, List.of(credential("new")), () -> true))
                .isSameAs(failure);

        assertThat(node.getByteArray("credentials", null)).containsExactly(before);
        assertThat(storage(file, lock).loadCredentials()).containsExactly(credential("old"));
    }

    @Test
    void constructionDoesNotReadSynchronizeOrCreateThePersistenceDirectory() {
        var node = org.mockito.Mockito.mock(java.util.prefs.Preferences.class);
        var lock = directory.resolve("not-created/credentials.lock");

        new PreferencesCredentialStorage(node, bytes -> { throw new AssertionError("encryption during construction"); },
                bytes -> { throw new AssertionError("decryption during construction"); }, lock);

        org.mockito.Mockito.verifyNoInteractions(node);
        assertThat(lock.getParent()).doesNotExist();
    }

    @Test
    void aDeniedComparisonDoesNotChangeOrFlushCredentials() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var seed = storage(file, lock);seed.saveCredentials(List.of(credential("old")));
        var before = seed.snapshot();
        var node = new PreferencesCredentialProcessTest.FilePreferences(file) {
            @Override protected void flushSpi() { throw new AssertionError("denied write was flushed"); }
        };
        var store = new PreferencesCredentialStorage(node, byte[]::clone, byte[]::clone, lock);

        assertThat(store.compareAndSet(before, List.of(credential("new")), () -> false)).isEmpty();

        assertThat(storage(file, lock).snapshot()).isEqualTo(before);
    }

    @Test
    void failedSynchronizationCannotEncodeOrOverwriteThePersistentValue() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var seed = storage(file, lock);seed.saveCredentials(List.of(credential("old")));
        var before = seed.snapshot();
        var failure = new java.util.prefs.BackingStoreException("synthetic sync failure");
        var failed = new java.util.concurrent.atomic.AtomicBoolean();
        var node = new PreferencesCredentialProcessTest.FilePreferences(file) {
            @Override protected void syncSpi() throws java.util.prefs.BackingStoreException {
                if (failed.get()) throw failure;
                super.syncSpi();
            }
        };
        failed.set(true);
        var store = new PreferencesCredentialStorage(node, bytes -> { throw new AssertionError("encoded before sync"); },
                byte[]::clone, lock);

        assertThatThrownBy(() -> store.compareAndSet(before, List.of(credential("new")), () -> true))
                .isInstanceOf(IllegalStateException.class).hasCause(failure);

        assertThat(storage(file, lock).snapshot()).isEqualTo(before);
    }

    @Test
    void failedCompensationRetainsBothFailuresAndRestoresTheInMemoryCiphertext() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var seed = storage(file, lock);seed.saveCredentials(List.of(credential("old")));
        var before = seed.snapshot();
        var failure = new java.util.prefs.BackingStoreException("synthetic publication failure");
        var compensation = new java.util.prefs.BackingStoreException("synthetic compensation failure");
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        var node = new PreferencesCredentialProcessTest.FilePreferences(file) {
            @Override protected void flushSpi() throws java.util.prefs.BackingStoreException {
                super.flushSpi();
                throw first.getAndSet(false) ? failure : compensation;
            }
        };
        var oldBytes = node.getByteArray("credentials", null);
        var store = new PreferencesCredentialStorage(node, byte[]::clone, byte[]::clone, lock);

        assertThatThrownBy(() -> store.compareAndSet(before, List.of(credential("new")), () -> true))
                .isInstanceOf(IllegalStateException.class).hasCause(failure)
                .satisfies(error -> assertThat(error.getSuppressed()).singleElement()
                        .satisfies(suppressed -> assertThat(suppressed)
                                .isInstanceOf(IllegalStateException.class).hasCause(compensation)));

        assertThat(node.getByteArray("credentials", null)).containsExactly(oldBytes);
        assertThat(storage(file, lock).snapshot()).isEqualTo(before);
    }

    @Test
    void waitingForAnotherStoreInTheSameJvmCanBeInterruptedBeforeItPublishes() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var initial = new CredentialStore.Snapshot("", List.of());
        var first = new PreferencesCredentialStorage(new PreferencesCredentialProcessTest.FilePreferences(file), bytes -> {
            entered.countDown();
            try {
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("first writer not released");
            } catch (InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
            return bytes;
        }, byte[]::clone, lock);
        var second = storage(file, lock);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var saving = workers.submit(() -> first.compareAndSet(initial, List.of(credential("first")), () -> true));
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var attempting = new java.util.concurrent.CountDownLatch(1);
                var current = new java.util.concurrent.atomic.AtomicReference<Thread>();
                var waiting = workers.submit(() -> {
                    current.set(Thread.currentThread());attempting.countDown();
                    return second.compareAndSet(initial, List.of(credential("second")), () -> true);
                });
                assertThat(attempting.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> waiting.get(150, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);

                current.get().interrupt();

                assertThatThrownBy(() -> waiting.get(2, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(java.io.UncheckedIOException.class);
            } finally { release.countDown(); }
            assertThat(saving.get(5, java.util.concurrent.TimeUnit.SECONDS)).isPresent();
        }
        assertThat(storage(file, lock).loadCredentials()).containsExactly(credential("first"));
    }

    @Test
    void aReentrantReaderUsingAnotherNodeStillRefreshesItsPersistentValue() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var older = storage(file, lock);
        var writer = storage(file, lock);
        writer.saveCredentials(List.of(credential("old")));
        var expected = writer.snapshot();

        var result = writer.compareAndSet(expected, List.of(credential("new")), () -> {
            assertThat(older.snapshot()).isEqualTo(expected);
            return true;
        });

        assertThat(result).isPresent();
        assertThat(storage(file, lock).loadCredentials()).containsExactly(credential("new"));
    }

    @Test
    void aReentrantChangeDuringThePermissionCallbackInvalidatesTheOuterComparison() throws Exception {
        var file = directory.resolve("synthetic.properties");
        var lock = directory.resolve("credentials.lock");
        var store = storage(file, lock);
        store.saveCredentials(List.of(credential("old")));
        var expected = store.snapshot();

        var result = store.compareAndSet(expected, List.of(credential("outer")), () -> {
            store.saveCredentials(List.of(credential("inner")));
            return true;
        });

        assertThat(result).isEmpty();
        assertThat(storage(file, lock).loadCredentials()).containsExactly(credential("inner"));
    }

    private static PreferencesCredentialStorage storage(Path file, Path lock) throws Exception {
        return new PreferencesCredentialStorage(new PreferencesCredentialProcessTest.FilePreferences(file),
                byte[]::clone, byte[]::clone, lock);
    }

    private static OAuth2Credential credential(String token) {
        var value = new OAuth2Credential("synthetic", token, null, "account", "Synthetic", 3600, List.of(), Map.of());
        value.setReceivedAt(Instant.EPOCH);
        return value;
    }
}
