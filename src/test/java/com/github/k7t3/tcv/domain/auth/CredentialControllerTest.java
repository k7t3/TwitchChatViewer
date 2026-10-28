package com.github.k7t3.tcv.domain.auth;

import com.github.k7t3.tcv.app.secure.KeyManagerException;

import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialControllerTest {

    @Test
    void deviceFlowPersistenceFailureIsDeliveredSoTheLoaderCanEndAuthentication() {
        try (var fixture = new Fixture()) {
            var results = new ArrayList<Boolean>();
            fixture.controller.startAuthenticate(results::add);
            var authenticated = credential();
            fixture.controller.getCredentialManager().getCredentials().add(authenticated);
            var failure = new KeyManagerException("cannot persist key");
            Mockito.doThrow(failure).when(fixture.store).saveCredentials(anyList());

            fixture.callback.accept(new DeviceTokenResponse(authenticated, null));

            assertThat(results).containsExactly(false);
            assertThat(fixture.controller.getCredential()).isNull();
        }
    }

    @Test
    void persistenceFailureIsReportedWithoutConvertingItToTokenInvalidation() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var flow = mock(DeviceFlowController.class);
        var identity = mock(TwitchIdentityProvider.class);
        var store = mock(CredentialStore.class);
        var original = credential();
        var failure = new KeyManagerException("key store is unavailable");
        when(identity.getProviderName()).thenReturn("twitch");
        when(identity.getProviderType()).thenReturn("oauth2");
        when(store.loadCredentials()).thenReturn(new ArrayList<>(List.of(original)));
        when(identity.refreshCredential(original)).thenReturn(Optional.of(credential()));
        Mockito.doThrow(failure).when(store).saveCredentials(anyList());
        try (var controller = new CredentialController(store, identity, flow, scheduler)) {
            assertThatThrownBy(controller::refreshToken).isSameAs(failure);
            assertThat(controller.getCredential()).isSameAs(original);
        } finally { scheduler.shutdownNow(); }
    }

    @Test
    void storageCancellationDuringRefreshIsNotConvertedToAnInvalidCredential() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var flow = mock(DeviceFlowController.class);
        var identity = mock(TwitchIdentityProvider.class);
        var store = mock(CredentialStore.class);
        var original = credential();
        var cancelled = new CancellationException("session ended");
        when(identity.getProviderName()).thenReturn("twitch");
        when(identity.getProviderType()).thenReturn("oauth2");
        when(store.loadCredentials()).thenReturn(new ArrayList<>(List.of(original)));
        when(identity.refreshCredential(original)).thenReturn(Optional.of(credential()));
        Mockito.doThrow(cancelled).when(store).saveCredentials(anyList());
        try (var controller = new CredentialController(store, identity, flow, scheduler)) {
            assertThatThrownBy(controller::refreshToken).isSameAs(cancelled);
        } finally { scheduler.shutdownNow(); }
    }

    @Test
    void failingCredentialReadClosesItsOwnedAuthenticationResources() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var flow = mock(DeviceFlowController.class);
        var identity = mock(TwitchIdentityProvider.class);
        var store = mock(CredentialStore.class);
        var failure = new IllegalStateException("credential storage is unavailable");
        when(store.loadCredentials()).thenThrow(failure);
        try {
            assertThatThrownBy(() -> new CredentialController(store, identity, flow, scheduler))
                    .isSameAs(failure);

            assertThat(scheduler.isShutdown()).isTrue();
            verify(flow).close();
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void closedControllerRejectsNewAuthentication() {
        try (var fixture = new Fixture()) {
            fixture.controller.close();

            assertThatThrownBy(() -> fixture.controller.startAuthenticate(result -> {}))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void closedFlowDoesNotPersistOrDeliverLateCredential() {
        try (var fixture = new Fixture()) {
            var results = new ArrayList<Boolean>();
            fixture.controller.startAuthenticate(results::add);
            fixture.controller.close();

            fixture.callback.accept(new DeviceTokenResponse(credential(), null));

            assertThat(results).isEmpty();
            assertThat(fixture.controller.getCredential()).isNull();
            verify(fixture.store, never()).saveCredentials(anyList());
        }
    }

    @Test
    void successfulDeviceFlowUpdatesAuthorizationState() {
        try (var fixture = new Fixture()) {
            var results = new ArrayList<Boolean>();
            fixture.controller.startAuthenticate(results::add);
            var credential = credential();
            // DeviceFlowController はコールバック前に CredentialManager へ資格情報を追加する。
            fixture.controller.getCredentialManager().getCredentials().add(credential);

            fixture.callback.accept(new DeviceTokenResponse(credential, null));

            assertThat(fixture.controller.isAuthorized()).isTrue();
            assertThat(fixture.controller.getCredential()).isSameAs(credential);
            assertThat(results).containsExactly(true);
        }
    }

    private static OAuth2Credential credential() {
        return new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600,
                List.of("chat:read"), Map.of());
    }

    private static final class Fixture implements AutoCloseable {
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private final TwitchIdentityProvider identity = mock(TwitchIdentityProvider.class);
        private final CredentialStore store = mock(CredentialStore.class);
        private final DeviceFlowController flow = mock(DeviceFlowController.class);
        private final CredentialController controller;
        private Consumer<DeviceTokenResponse> callback;

        private Fixture() {
            when(identity.getProviderName()).thenReturn("twitch");
            when(store.loadCredentials()).thenReturn(new ArrayList<>());
            when(flow.startOAuth2DeviceAuthorizationGrantType(any(), anyCollection(), any()))
                    .thenAnswer(invocation -> {
                        callback = invocation.getArgument(2);
                        return new DeviceAuthorization("device", "CODE", "https://example.test", 60, 1,
                                "https://example.test/complete", Map.of(), Instant.EPOCH);
                    });
            controller = new CredentialController(store, identity, flow, scheduler);
        }

        @Override
        public void close() {
            try {
                controller.close();
            } finally {
                scheduler.shutdownNow();
            }
        }
    }

    @Test
    void disposingAuthenticationStopsOwnedScheduler() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var identity = mock(TwitchIdentityProvider.class);
        when(identity.getProviderName()).thenReturn("twitch");
        var store = mock(CredentialStore.class);
        when(store.loadCredentials()).thenReturn(List.of());
        var flow = mock(DeviceFlowController.class);
        var controller = new CredentialController(store, identity, flow, scheduler);
        try {
            controller.disposeAuthenticate();

            assertThat(scheduler.isShutdown()).isTrue();
        } finally {
            scheduler.shutdownNow();
        }
    }
}
