package com.github.k7t3.tcv.domain.auth;

import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCredentialStoreTest {
    @Test
    void sdkCanEnrichADetachedCredentialWithoutChangingTheStoredSession() {
        var backing = new MemoryStore();
        var previous = new OAuth2Credential("twitch", "old", "refresh", "123", "User", 3600, List.of("chat:read"));
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        var sdkCopy = (OAuth2Credential) scope.loadCredentials().getFirst();
        var enriched = new OAuth2Credential("twitch", "enriched", "next", "123", "User", 3600,
                List.of("chat:read", "user:read:follows"));

        sdkCopy.updateCredential(enriched);

        assertThat(sdkCopy.getAccessToken()).isEqualTo("enriched");
        assertThat(sdkCopy.getScopes()).containsExactly("chat:read", "user:read:follows");
        assertThat(scope.credential().getAccessToken()).isEqualTo("old");
        assertThat(scope.credential().getScopes()).containsExactly("chat:read");
        assertThat(((OAuth2Credential) backing.loadCredentials().getFirst()).getScopes()).containsExactly("chat:read");
    }

    @Test
    void aCommittedRefreshUpdatesAnSdkCredentialWithTheNewScopes() {
        var backing = new MemoryStore();
        var previous = new OAuth2Credential("twitch", "old", "refresh", "123", "User", 3600, List.of("chat:read"));
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        var sdkCopy = (OAuth2Credential) scope.loadCredentials().getFirst();
        scope.onUpdated(sdkCopy::updateCredential);
        var refreshed = new OAuth2Credential("twitch", "refreshed", "next", "123", "User", 3600,
                List.of("chat:read", "user:read:follows"));

        scope.saveCredentials(List.of(refreshed));

        assertThat(sdkCopy.getAccessToken()).isEqualTo("refreshed");
        assertThat(sdkCopy.getScopes()).containsExactly("chat:read", "user:read:follows");
        assertThat(scope.credential().getAccessToken()).isEqualTo("refreshed");
        assertThat(scope.credential().getScopes()).containsExactly("chat:read", "user:read:follows");
        assertThat(backing.loadCredentials()).containsExactly(refreshed);
    }

    @Test
    void sdkAndApiReadOnlyTheirOwnSessionAfterAnotherAccountHasBeenStored() {
        var backing = new MemoryStore();
        var previous = credential("old");
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        var current = credential("new");
        backing.saveCredentials(List.of(current));

        assertThat(scope.loadCredentials()).containsExactly(previous);
        assertThat(scope.getCredentialByUserId("123")).contains(previous);
        assertThatThrownBy(() -> scope.saveCredentials(List.of(credential("old-refreshed"))))
                .isInstanceOf(InvalidCredentialException.class);
        assertThat(backing.loadCredentials()).containsExactly(current);
    }

    @Test
    void successfulRefreshUpdatesOnlyTheOwnedCredentialAndReturnsDetachedValues() {
        var backing = new MemoryStore();
        var previous = credential("old");
        var other = credential("other");
        other.setUserId("456");
        backing.saveCredentials(List.of(previous, other));
        var scope = new SessionCredentialStore(backing, previous);
        var updated = credential("refreshed");

        scope.saveCredentials(List.of(updated));
        ((OAuth2Credential) scope.loadCredentials().getFirst()).setAccessToken("caller mutation");

        assertThat(scope.credential()).isEqualTo(updated);
        assertThat(backing.loadCredentials()).containsExactly(updated, other);
        assertThat(scope.tokens()).contains("old", "refreshed");
    }

    @Test
    void aRetiredScopeRejectsLateWritesAndCannotDeleteNewlyStoredCredentials() {
        var backing = new MemoryStore();
        var previous = credential("old");
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        scope.retire();
        var current = credential("new");
        backing.saveCredentials(List.of(current));

        assertThatThrownBy(() -> scope.saveCredentials(List.of(credential("late"))))
                .isInstanceOf(CancellationException.class);
        assertThat(scope.removeCredential(previous)).isFalse();
        assertThat(backing.loadCredentials()).containsExactly(current);
    }

    @Test
    void aReplacedLeaseReportsInvalidationOnlyOnce() {
        var backing = new MemoryStore();
        var previous = credential("old");
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        var invalidations = new AtomicInteger();
        scope.onInvalidated(invalidations::incrementAndGet);
        backing.saveCredentials(List.of(credential("new")));

        assertThatThrownBy(() -> scope.saveCredentials(List.of(credential("stale"))))
                .isInstanceOf(InvalidCredentialException.class);
        assertThatThrownBy(() -> scope.saveCredentials(List.of(credential("stale-again"))))
                .isInstanceOf(InvalidCredentialException.class);
        assertThat(invalidations).hasValue(1);
    }

    @Test
    void retirementDoesNotWaitForStorageAndCancelsACommitThatHasNotPassedItsPermissionCheck() throws Exception {
        var backing = new MemoryStore();
        var previous = credential("old");
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        backing.beforeCheck = () -> { entered.countDown(); await(release); };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var saving = workers.submit(() -> scope.saveCredentials(List.of(credential("late"))));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                workers.submit(scope::retire).get(1, TimeUnit.SECONDS);
                release.countDown();
                assertThatThrownBy(() -> saving.get(5, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(CancellationException.class);
                assertThat(backing.loadCredentials()).containsExactly(previous);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void newAuthenticationReadsAfterACommitThatAlreadyPassedItsPermissionCheck() throws Exception {
        var backing = new MemoryStore();
        var previous = credential("old");
        backing.saveCredentials(List.of(previous));
        var scope = new SessionCredentialStore(backing, previous);
        var refreshed = credential("refreshed");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        backing.afterCheck = () -> { entered.countDown(); await(release); };
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var saving = workers.submit(() -> scope.saveCredentials(List.of(refreshed)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                workers.submit(scope::retire).get(1, TimeUnit.SECONDS);
                var authenticating = workers.submit(() -> new SessionCredentialStore(backing));
                assertThatThrownBy(() -> authenticating.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                saving.get(5, TimeUnit.SECONDS);
                var next = authenticating.get(5, TimeUnit.SECONDS);
                assertThat(next.loadCredentials()).containsExactly(refreshed);
                next.saveCredentials(List.of(credential("new-authentication")));
                assertThat(backing.loadCredentials()).containsExactly(credential("new-authentication"));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void aCancelledAuthenticationCannotPersistItsLateDeviceFlowResult() {
        var backing = new MemoryStore();
        var scope = new SessionCredentialStore(backing);
        scope.retire();
        var current = credential("new");
        backing.saveCredentials(List.of(current));

        assertThatThrownBy(() -> scope.saveCredentials(List.of(credential("late"))))
                .isInstanceOf(CancellationException.class);
        assertThat(backing.loadCredentials()).containsExactly(current);
    }

    private static OAuth2Credential credential(String token) {
        var value = new OAuth2Credential("twitch", token, "refresh-" + token, "123", "User", 3600, List.of());
        value.setReceivedAt(java.time.Instant.ofEpochMilli(1680000000000L));
        return value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("storage was not released");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class MemoryStore implements CredentialStore {
        private List<Credential> values = new ArrayList<>();
        private long revision;
        volatile Runnable beforeCheck = () -> {};
        volatile Runnable afterCheck = () -> {};
        @Override public synchronized List<Credential> loadCredentials() { return List.copyOf(values); }
        @Override public synchronized void saveCredentials(List<Credential> values) { this.values = new ArrayList<>(values); revision++; }
        @Override public synchronized Optional<Credential> getCredentialByUserId(String id) {
            return values.stream().filter(value -> Objects.equals(value.getUserId(), id)).findFirst();
        }
        @Override public synchronized void clearCredentials() { values.clear(); revision++; }
        @Override public synchronized boolean removeCredential(OAuth2Credential expected) {
            var removed = values.removeIf(value -> value instanceof OAuth2Credential oauth
                    && Objects.equals(oauth.getIdentityProvider(), expected.getIdentityProvider())
                    && Objects.equals(oauth.getAccessToken(), expected.getAccessToken()));
            if (removed) revision++;
            return removed;
        }
        @Override public synchronized Snapshot snapshot() { return new Snapshot(Long.toString(revision), values); }
        @Override public synchronized Optional<Snapshot> compareAndSet(Snapshot expected, List<Credential> next, BooleanSupplier permitted) {
            beforeCheck.run();
            if (!expected.revision().equals(Long.toString(revision)) || !permitted.getAsBoolean()) return Optional.empty();
            afterCheck.run();
            afterCheck = () -> {};
            saveCredentials(next);
            return Optional.of(snapshot());
        }
    }
}
