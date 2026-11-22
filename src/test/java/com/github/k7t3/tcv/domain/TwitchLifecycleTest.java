package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TwitchLifecycleTest {
    @Test
    void invalidationNotifiesItsOwnerWithoutClosingTheClientAndOwnerCloseRemovesOnlyItsCredential() {
        var own = credential("old");
        var store = new MemoryStore(own);
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(own, store, client);
        var notices = new AtomicInteger();
        twitch.onInvalidated(notices::incrementAndGet);
        try (var identities = mockConstruction(TwitchIdentityProvider.class)) {
            twitch.invalidateCredentials();
            twitch.invalidateCredentials();
            assertThat(notices).hasValue(1);
            verify(client, never()).close();
            assertThat(store.loadCredentials()).containsExactly(own);
            twitch.close();
            assertThat(store.loadCredentials()).isEmpty();
            assertThat(identities.constructed()).isEmpty();
            verify(client).close();
        }
    }

    @Test
    void refreshPersistsOnlyTheOwnedSessionAndKeepsSdkAndMetadataInSync() {
        var own = credential("old");
        var store = new MemoryStore(own);
        var twitch = new Twitch(own, store, mock(TwitchClients.class));
        var refreshed = credential("rotated");
        try {
            twitch.getCredentialStore().saveCredentials(List.of(refreshed));
            assertThat(twitch.getAccessToken()).isEqualTo("rotated");
            assertThat(own.getAccessToken()).isEqualTo("rotated");
            assertThat(twitch.credentialTokens()).contains("old", "rotated");
            twitch.freezeCredentials();
            assertThatThrownBy(() -> twitch.getCredentialStore().saveCredentials(List.of(credential("late"))))
                    .isInstanceOf(CancellationException.class);
            assertThat(twitch.getAccessToken()).isEqualTo("rotated");
        } finally { twitch.close(); }
    }

    @Test
    void logoutReportsRejectedRevocationAfterRemovingItsStoredCredential() {
        var own = credential("old");
        var store = new MemoryStore(own);
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(own, store, client);
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) ->
                when(identity.revokeCredential(own)).thenReturn(false))) {
            assertThatThrownBy(twitch::logout).isInstanceOf(IllegalStateException.class).hasMessageContaining("revoke");

            assertThat(store.loadCredentials()).isEmpty();
            verify(client).close();
        }
    }

    @Test
    void concurrentCallersShareOneApiInstance() throws Exception {
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(credential("old"), new MemoryStore(credential("old")), client);
        var start = new CountDownLatch(1);
        var apis = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<TwitchAPI, Boolean>()));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = new ArrayList<java.util.concurrent.Future<TwitchAPI>>();
            for (var i = 0; i < 8; i++) calls.add(workers.submit(() -> {
                start.await();
                var api = twitch.getTwitchAPI();
                apis.add(api);
                return api;
            }));
            start.countDown();
            for (var call : calls) call.get(5, TimeUnit.SECONDS);
            assertThat(apis).hasSize(1);
        } finally {
            twitch.close();
            for (var api : apis) api.close();
        }
    }

    @Test
    void logoutAttemptsRevocationAndRemovalEvenAfterClientCloseFails() {
        var own = credential("old");
        var store = new MemoryStore(own);
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(own, store, client);
        var first = new IllegalStateException("client close failed");
        var second = new IllegalStateException("revocation failed");
        doThrow(first).when(client).close();
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) ->
                doThrow(second).when(identity).revokeCredential(own))) {
            assertThatThrownBy(twitch::logout).isSameAs(first);

            assertThat(first.getSuppressed()).containsExactly(second);
            assertThat(store.loadCredentials()).isEmpty();
        }
    }

    @Test
    void aClosedClientCannotRecreateItsApiOrStartAnotherChatOperation() {
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(credential("old"), new MemoryStore(credential("old")), client);
        try (var apis = mockConstruction(TwitchAPI.class)) {
            twitch.getTwitchAPI();
            twitch.close();

            assertThatThrownBy(twitch::getTwitchAPI).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(twitch::getChat).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(twitch::getClient).isInstanceOf(IllegalStateException.class);
            assertThat(apis.constructed()).hasSize(1);
        }
    }

    @Test
    void repeatedCloseReleasesItsResourcesOnce() throws Exception {
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(credential("old"), new MemoryStore(credential("old")), client);
        try (var apis = mockConstruction(TwitchAPI.class)) {
            var api = twitch.getTwitchAPI();

            twitch.close();
            twitch.close();

            verify(api).close();
            verify(client).close();
        }
    }

    @Test
    void credentialFreezeFailureStillReleasesTheApiAndClientWithoutRemovingStoredCredentials() throws Exception {
        var own = credential("old");
        var store = new MemoryStore(own);
        var client = mock(TwitchClients.class);
        var twitch = spy(new Twitch(own, store, client));
        var failure = new IllegalStateException("cannot stop credential updates");
        try (var apis = mockConstruction(TwitchAPI.class)) {
            var api = twitch.getTwitchAPI();
            doThrow(failure).when(twitch).freezeCredentials();

            assertThatThrownBy(twitch::close).isSameAs(failure);

            verify(api).close();
            verify(client).close();
            assertThat(twitch.isClosed()).isTrue();
            assertThat(store.loadCredentials()).containsExactly(own);
            twitch.close();
            verify(api).close();
            verify(client).close();
        }
    }

    @Test
    void apiCloseFailureIsReportedAfterTheClientIsAlsoClosed() throws Exception {
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(credential("old"), new MemoryStore(credential("old")), client);
        var apiFailure = new IOException("api close failed");
        var clientFailure = new IllegalStateException("client close failed");
        try (var apis = mockConstruction(TwitchAPI.class)) {
            var api = twitch.getTwitchAPI();
            doThrow(apiFailure).when(api).close();
            doThrow(clientFailure).when(client).close();

            assertThatThrownBy(twitch::close).isInstanceOf(UncheckedIOException.class)
                    .hasCause(apiFailure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(clientFailure));

            verify(client).close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logoutRevokesOnlyItsOwnCredentialAndKeepsANewerStoredCredential(boolean newer) {
        var own = credential("old");
        var saved = newer ? credential("new") : own;
        var store = new MemoryStore(saved);
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(own, store, client);
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.revokeCredential(any(OAuth2Credential.class))).thenReturn(true);
        })) {
            twitch.logout();

            assertThat(identities.constructed()).hasSize(1);
            verify(identities.constructed().getFirst()).revokeCredential(own);
            if (newer) assertThat(store.loadCredentials()).containsExactly(saved);
            else assertThat(store.loadCredentials()).isEmpty();
            verify(client).close();
        }
    }

    @Test
    void repeatedLogoutDoesNotRevokeOrCloseAgain() {
        var own = credential("old");
        var store = new MemoryStore(own);
        var client = mock(TwitchClients.class);
        var twitch = new Twitch(own, store, client);
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.revokeCredential(any(OAuth2Credential.class))).thenReturn(true);
        })) {
            twitch.logout();
            twitch.logout();
            twitch.close();

            assertThat(identities.constructed()).hasSize(1);
            verify(identities.constructed().getFirst()).revokeCredential(own);
            verify(client).close();
        }
    }

    private static OAuth2Credential credential(String token) {
        return new OAuth2Credential("twitch", token, "refresh-" + token, "123", "User", 3600, List.of());
    }

    private static final class MemoryStore implements CredentialStore {
        private final List<Credential> values = new ArrayList<>();
        private long revision;

        MemoryStore(Credential initial) { values.add(initial); }

        @Override
        public synchronized Snapshot snapshot() { return new Snapshot(Long.toString(revision), values); }

        @Override
        public synchronized Optional<Snapshot> compareAndSet(Snapshot expected, List<Credential> next, BooleanSupplier permitted) {
            if (!expected.revision().equals(Long.toString(revision)) || !permitted.getAsBoolean()) return Optional.empty();
            saveCredentials(next);
            return Optional.of(snapshot());
        }

        @Override
        public synchronized List<Credential> loadCredentials() { return List.copyOf(values); }

        @Override
        public synchronized void saveCredentials(List<Credential> credentials) {
            values.clear();
            values.addAll(credentials);
            revision++;
        }

        @Override
        public synchronized Optional<Credential> getCredentialByUserId(String id) {
            return values.stream().filter(value -> Objects.equals(id, value.getUserId())).findFirst();
        }

        @Override
        public synchronized void clearCredentials() { values.clear(); revision++; }

        @Override
        public synchronized boolean removeCredential(OAuth2Credential expected) {
            var removed = values.removeIf(value -> value instanceof OAuth2Credential oauth
                    && Objects.equals(oauth.getIdentityProvider(), expected.getIdentityProvider())
                    && Objects.equals(oauth.getAccessToken(), expected.getAccessToken()));
            if (removed) revision++;
            return removed;
        }
    }
}
