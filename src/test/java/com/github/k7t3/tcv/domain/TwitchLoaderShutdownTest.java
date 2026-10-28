package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwitchLoaderShutdownTest {
    @Test
    void theRealDeviceFlowSchedulerIsOwnedThroughClientConstructionAndItsTermination() throws Exception {
        var store = mock(CredentialStore.class);
        when(store.loadCredentials()).thenReturn(new ArrayList<>());
        var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pollingThread = new AtomicReference<Thread>();
        var client = mock(Twitch.class);
        var delivered = new CopyOnWriteArrayList<Optional<Twitch>>();
        try (var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
            when(identity.getProviderName()).thenReturn("twitch");
            when(identity.getProviderType()).thenReturn("oauth2");
            when(identity.createDeviceFlowRequest(anyCollection())).thenReturn(new DeviceAuthorization("device", "CODE",
                    "https://example.test", 60, 0, "https://example.test/complete", Map.of(), Instant.now()));
            when(identity.getDeviceAccessToken("device")).thenReturn(new DeviceTokenResponse(credential, null));
        })) {
            var controller = new CredentialController(store);
            try (var loader = new TwitchLoader(controller, value -> {
                pollingThread.set(Thread.currentThread());
                entered.countDown();
                await(release);
                return client;
            }); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                loader.startAuthenticate(delivered::add);
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    loader.close();
                    var completed = workers.submit(loader::awaitClosed);
                    assertThatThrownBy(() -> completed.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    release.countDown();
                    completed.get(5, TimeUnit.SECONDS);
                    pollingThread.get().join(5000);
                    assertThat(pollingThread.get().isAlive()).isFalse();
                    assertThat(delivered).isEmpty();
                    verify(client).close();
                    verify(store).saveCredentials(anyList());
                } finally { release.countDown(); }
            }
        } finally { release.countDown(); }
    }

    @Test
    void failureClosingALateClientIsRetainedForTheApplicationWaiter() throws Exception {
        var callback = new AtomicReference<Consumer<Boolean>>();
        var controller = controller(callback);
        var creating = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var client = mock(Twitch.class);
        var failure = new IllegalStateException("late client could not close");
        doThrow(failure).when(client).close();
        try (var loader = new TwitchLoader(controller, credential -> { creating.countDown(); await(release); return client; });
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            loader.startAuthenticate(result -> {});
            var polling = workers.submit(() -> callback.get().accept(true));
            try {
                assertThat(creating.await(5, TimeUnit.SECONDS)).isTrue();
                loader.close();
                release.countDown();
                assertThatThrownBy(() -> polling.get(5, TimeUnit.SECONDS)).hasCause(failure);
                assertThatThrownBy(loader::awaitClosed).isSameAs(failure);
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void waitingForCloseIncludesTheRunningCallbackAndItsUnacceptedClientCleanup() throws Exception {
        var callback = new AtomicReference<Consumer<Boolean>>();
        var controller = controller(callback);
        var building = new CountDownLatch(1);
        var finishBuilding = new CountDownLatch(1);
        var disposing = new CountDownLatch(1);
        var finishDisposing = new CountDownLatch(1);
        var client = mock(Twitch.class);
        var delivered = new CopyOnWriteArrayList<Optional<Twitch>>();
        doAnswer(invocation -> { disposing.countDown(); await(finishDisposing); return null; }).when(client).close();
        try (var loader = new TwitchLoader(controller, credential -> { building.countDown(); await(finishBuilding); return client; });
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            loader.startAuthenticate(delivered::add);
            var polling = workers.submit(() -> callback.get().accept(true));
            try {
                assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();
                loader.close();
                var completed = workers.submit(loader::awaitClosed);
                assertThatThrownBy(() -> completed.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                finishBuilding.countDown();
                assertThat(disposing.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(completed.isDone()).isFalse();
                finishDisposing.countDown();
                polling.get(5, TimeUnit.SECONDS);
                completed.get(5, TimeUnit.SECONDS);
                assertThat(delivered).isEmpty();
                verify(client).close();
            } finally { finishBuilding.countDown(); finishDisposing.countDown(); }
        } finally { finishBuilding.countDown(); finishDisposing.countDown(); }
    }

    @Test
    void waitingForCloseAlsoIncludesSavedCredentialClientConstruction() throws Exception {
        var controller = controller(new AtomicReference<>());
        var credential = new OAuth2Credential("twitch", "access");
        var manager = mock(CredentialManager.class);
        when(manager.getCredentials()).thenReturn(List.of(credential));
        when(controller.getCredentialManager()).thenReturn(manager);
        when(controller.isAuthorized()).thenReturn(true);
        when(controller.validateToken()).thenReturn(true);
        var building = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var client = mock(Twitch.class);
        try (var loader = new TwitchLoader(controller, value -> { building.countDown(); await(release); return client; });
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var loading = workers.submit(loader::load);
            try {
                assertThat(building.await(5, TimeUnit.SECONDS)).isTrue();
                loader.close();
                var completed = workers.submit(loader::awaitClosed);
                assertThatThrownBy(() -> completed.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(loading.get(5, TimeUnit.SECONDS)).isEmpty();
                completed.get(5, TimeUnit.SECONDS);
                verify(client).close();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void aCallbackCanRequestCloseButWaitingForItselfIsRejected() {
        var callback = new AtomicReference<Consumer<Boolean>>();
        var controller = controller(callback);
        var reference = new AtomicReference<TwitchLoader>();
        try (var loader = new TwitchLoader(controller, credential -> mock(Twitch.class))) {
            reference.set(loader);
            loader.startAuthenticate(result -> {
                reference.get().close();
                assertThatThrownBy(reference.get()::awaitClosed).isInstanceOf(IllegalStateException.class);
            });
            callback.get().accept(true);
            loader.awaitClosed();
            verify(controller).close();
            verify(controller).awaitAuthenticationStopped();
        }
    }

    @Test
    void aClosedLoaderCannotStartAnotherDeviceFlow() {
        var callback = new AtomicReference<Consumer<Boolean>>();
        var controller = controller(callback);
        try (var loader = new TwitchLoader(controller, credential -> mock(Twitch.class))) {
            loader.close();
            assertThatThrownBy(() -> loader.startAuthenticate(result -> {})).isInstanceOf(IllegalStateException.class);
            verify(controller, never()).startAuthenticate(any());
        }
    }

    private static CredentialController controller(AtomicReference<Consumer<Boolean>> callback) {
        var controller = mock(CredentialController.class);
        when(controller.startAuthenticate(any())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return new CredentialController.DeviceFlow("CODE", "https://example.test", "https://example.test/complete");
        });
        return controller;
    }

    private static void await(CountDownLatch latch) {
        while (true) {
            try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); return; }
            catch (InterruptedException ignored) { }
        }
    }
}
