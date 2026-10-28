package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.auth.SessionCredentialStore;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwitchLoaderTest {
    @Test
    void aRealDeviceFlowEnrichesUserIdentityBeforeTheClientIsAdopted() throws Exception {
        var raw = new OAuth2Credential("twitch", "access", "refresh", null, null, 3600, List.of("chat:read"));
        var enriched = new OAuth2Credential("twitch", "access", "refresh", "123", "User", 3600, List.of("chat:read"));
        var backing = mock(CredentialStore.class);
        when(backing.loadCredentials()).thenReturn(new ArrayList<>());
        var received = new AtomicReference<OAuth2Credential>();
        var delivered = new java.util.concurrent.CompletableFuture<Optional<Twitch>>();
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.getProviderType()).thenReturn("oauth2");
            when(identity.createDeviceFlowRequest(anyCollection())).thenReturn(
                    new com.github.philippheuer.credentialmanager.domain.DeviceAuthorization(
                            "device", "CODE", "https://example.test", 60, 0,
                            "https://example.test/complete", java.util.Map.of(), java.time.Instant.now()));
            when(identity.getDeviceAccessToken("device")).thenReturn(
                    new com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse(raw, null));
            when(identity.getAdditionalCredentialInformation(any())).thenReturn(Optional.of(enriched));
            when(identity.isCredentialValid(any())).thenReturn(Optional.of(true));
        })) {
            var client = mock(Twitch.class);
            var controller = new CredentialController(backing);
            var loader = new TwitchLoader(controller, credential -> { received.set(credential); return client; });
            try {
                loader.startAuthenticate(delivered::complete);

                assertThat(delivered.get(5, TimeUnit.SECONDS)).contains(client);
                assertThat(received.get().getUserId()).isEqualTo("123");
                assertThat(received.get().getUserName()).isEqualTo("User");
                assertThat(received.get().getAccessToken()).isEqualTo("access");
                verify(identities.constructed().getFirst()).getAdditionalCredentialInformation(raw);
                verify(backing).saveCredentials(argThat(credentials -> credentials.size() == 1
                        && credentials.getFirst().getUserId().equals("123")));
            } finally {
                loader.close(); loader.awaitClosed(); client.close();
            }
        }
    }

    @Test
    void theProductionClientCanEnrichTheScopedCredentialWithoutFailingConstruction() {
        var own = new OAuth2Credential("twitch", "test-token", "refresh", "123", "User", 3600,
                List.of("chat:read", "user:read:follows"));
        var backing = mock(CredentialStore.class);
        when(backing.snapshot()).thenReturn(new CredentialStore.Snapshot("1", List.of(own)));
        var chat = mock(TwitchChat.class); var builder = mock(TwitchChatBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chat);
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.isCredentialValid(any())).thenReturn(Optional.of(true));
            when(identity.getAdditionalCredentialInformation(any())).thenAnswer(call -> {
                OAuth2Credential incoming = call.getArgument(0);
                var enriched = new OAuth2Credential("twitch", incoming.getAccessToken(), "refresh", "123", "User", 3600,
                        List.of("chat:read", "user:read:follows"));
                enriched.getContext().put("client_id", "test-client");
                return Optional.of(enriched);
            });
        }); var builders = mockStatic(TwitchChatBuilder.class)) {
            builders.when(TwitchChatBuilder::builder).thenReturn(builder);
            try (var loader = new TwitchLoader(backing); var twitch = loader.load().orElseThrow()) {
                assertThat(twitch.getAccessToken()).isEqualTo("test-token");
                assertThat(twitch.getUserId()).isEqualTo("123");
                var scoped = (OAuth2Credential) twitch.getCredentialStore().loadCredentials().getFirst();
                var enriched = identities.constructed().getFirst().getAdditionalCredentialInformation(scoped).orElseThrow();
                scoped.updateCredential(enriched);
                assertThat(scoped.getScopes()).containsExactly("chat:read", "user:read:follows");
                assertThat(twitch.getCredentialStore().loadCredentials()).hasSize(1);
            }
            verify(chat).close();
        }
    }


    @Test
    void sdkUsesTheSessionStoreAndClosingTheLoaderDoesNotRetireAnAdoptedClient() {
        var backing = mock(CredentialStore.class);
        var own = new OAuth2Credential("twitch", "old", "refresh", "123", "User", 3600, List.of());
        var snapshot = new CredentialStore.Snapshot("1", List.of(own));
        when(backing.snapshot()).thenReturn(snapshot);
        when(backing.loadCredentials()).thenReturn(snapshot.credentials());
        var client = mock(TwitchClients.class);
        var resources = mock(TwitchClientFactory.Resources.class);
        when(resources.client()).thenReturn(client);
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.isCredentialValid(any())).thenReturn(Optional.of(true));
        }); var factories = mockStatic(TwitchClientFactory.class)) {
            factories.when(() -> TwitchClientFactory.create(any(), any(), any())).thenReturn(resources);
            var loader = new TwitchLoader(backing);
            var twitch = loader.load().orElseThrow();
            try {
                var managers = org.mockito.ArgumentCaptor.forClass(CredentialManager.class);
                var cancelled = org.mockito.ArgumentCaptor.forClass(java.util.function.BooleanSupplier.class);
                factories.verify(() -> TwitchClientFactory.create(managers.capture(), eq(own), cancelled.capture()));
                var scoped = (SessionCredentialStore) managers.getValue().getStorageBackend();
                assertThat(scoped).isSameAs(twitch.getCredentialStore());
                assertThat(scoped.loadCredentials()).containsExactly(own);
                assertThat(cancelled.getValue().getAsBoolean()).isFalse();
                loader.close();
                assertThat(scoped.isRetired()).isFalse();
                assertThat(cancelled.getValue().getAsBoolean()).isFalse();
                twitch.close();
                assertThat(scoped.isRetired()).isTrue();
                assertThat(cancelled.getValue().getAsBoolean()).isTrue();
                verify(resources).close();
                verify(client, never()).close();
            } finally { loader.close(); twitch.close(); }
        }
    }

    @Test
    void closingAnAuthenticationAttemptRetiresTheActualSdkStorageBeforeLateWrites() {
        var backing = mock(CredentialStore.class);
        when(backing.snapshot()).thenReturn(new CredentialStore.Snapshot("0", List.of()));
        var controllers = new ArrayList<CredentialController>();
        var stores = new ArrayList<CredentialStore>();
        try (var construction = mockConstruction(CredentialController.class, (controller, context) -> {
            controllers.add(controller);
            stores.add((CredentialStore) context.arguments().getFirst());
        })) {
            var loader = new TwitchLoader(backing);
            loader.close();
            assertThat(stores).hasSize(1);
            var store = stores.getFirst();
            assertThat(store).isInstanceOf(SessionCredentialStore.class);
            Assertions.assertThatThrownBy(() -> store.saveCredentials(List.of()))
                    .isInstanceOf(CancellationException.class);
            verify(backing, never()).compareAndSet(any(), any(), any());
            verify(controllers.getFirst()).close();
        }
    }

    @Test
    void clientConstructionFailureReportsAnUnsuccessfulAuthentication() {
        var controller = mock(CredentialController.class);
        var callback = new AtomicReference<Consumer<Boolean>>();
        when(controller.startAuthenticate(any())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return new CredentialController.DeviceFlow("CODE", "https://example.test", "https://example.test/complete");
        });
        var results = new ArrayList<Optional<Twitch>>();
        try (var loader = new TwitchLoader(controller, credential -> {
            throw new IllegalStateException("client construction failed");
        })) {
            loader.startAuthenticate(results::add);

            callback.get().accept(true);

            assertThat(results).containsExactly(Optional.empty());
            verify(controller).close();
        }
    }

    @Test
    void closeDuringClientConstructionDisposesTheUnacceptedClient() throws Exception {
        var controller = mock(CredentialController.class);
        var callback = new AtomicReference<Consumer<Boolean>>();
        when(controller.startAuthenticate(any())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return new CredentialController.DeviceFlow("CODE", "https://example.test", "https://example.test/complete");
        });
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var client = mock(Twitch.class);
        var delivered = new ArrayList<Optional<Twitch>>();
        try (var loader = new TwitchLoader(controller, credential -> {
            started.countDown();
            try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            return client;
        }); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            loader.startAuthenticate(delivered::add);
            var callbackTask = executor.submit(() -> callback.get().accept(true));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                loader.close();

                release.countDown();
                callbackTask.get(5, TimeUnit.SECONDS);

                assertThat(delivered).isEmpty();
                verify(client).close();
            } finally {
                release.countDown();
                callbackTask.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void buildsClientBeforeClosingItsPollingThread() {
        var controller = mock(CredentialController.class);
        var callback = new AtomicReference<Consumer<Boolean>>();
        when(controller.startAuthenticate(any())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return new CredentialController.DeviceFlow("CODE", "https://example.test", "https://example.test/complete");
        });
        doAnswer(invocation -> { Thread.currentThread().interrupt(); return null; }).when(controller).close();
        doAnswer(invocation -> { Thread.currentThread().interrupt(); return null; }).when(controller).disposeAuthenticate();
        var client = mock(Twitch.class);
        var delivered = new ArrayList<Optional<Twitch>>();
        try (var loader = new TwitchLoader(controller, credential -> {
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            return client;
        })) {
            loader.startAuthenticate(delivered::add);

            callback.get().accept(true);

            assertThat(delivered).containsExactly(Optional.of(client));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void closedLoaderDoesNotBuildOrDeliverLateClient() {
        var controller = mock(CredentialController.class);
        var callback = new AtomicReference<Consumer<Boolean>>();
        when(controller.startAuthenticate(any())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return new CredentialController.DeviceFlow("CODE", "https://example.test", "https://example.test/complete");
        });
        when(controller.getCredential()).thenReturn(mock(OAuth2Credential.class));
        var built = new ArrayList<Twitch>();
        var results = new ArrayList<Optional<Twitch>>();
        try (var loader = new TwitchLoader(controller, credential -> {
            var client = mock(Twitch.class);
            built.add(client);
            return client;
        })) {
            loader.startAuthenticate(results::add);
            loader.close();

            callback.get().accept(true);

            assertThat(built).isEmpty();
            assertThat(results).isEmpty();
        }
    }
}
