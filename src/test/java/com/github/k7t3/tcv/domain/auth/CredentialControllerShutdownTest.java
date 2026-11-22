package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceAuthorization;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialControllerShutdownTest {
    @Test
    void aReportedPersistenceFailureIsNotReplayedWhenTheRecipientClosesTheController() throws Exception {
        try (var fixture = new Fixture()) {
            var delivered = new CopyOnWriteArrayList<Boolean>();
            fixture.controller.startAuthenticate(result -> {
                delivered.add(result);
                fixture.controller.close();
            });
            var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
            fixture.controller.getCredentialManager().getCredentials().add(credential);
            var failure = new com.github.k7t3.tcv.app.secure.KeyManagerException("credential persistence failed");
            doThrow(failure).when(fixture.store).saveCredentials(anyList());

            fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(credential, null)))
                    .get(5, TimeUnit.SECONDS);

            assertThatCode(fixture.controller::awaitAuthenticationStopped).doesNotThrowAnyException();
            assertThat(delivered).containsExactly(false);
            assertThat(fixture.controller.getCredential()).isNull();
            assertThat(fixture.scheduler.isTerminated()).isTrue();
        }
    }

    @Test
    void aFlowCloseFailureKeepsTheLaterPersistenceFailure() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture()) {
            var delivered = new CopyOnWriteArrayList<Boolean>();
            fixture.controller.startAuthenticate(delivered::add);
            var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
            fixture.controller.getCredentialManager().getCredentials().add(credential);
            var persistenceFailure = new com.github.k7t3.tcv.app.secure.KeyManagerException("credential persistence failed");
            var closeFailure = new IllegalStateException("flow could not close");
            doAnswer(invocation -> {
                entered.countDown();
                await(release);
                throw persistenceFailure;
            }).when(fixture.store).saveCredentials(anyList());
            doThrow(closeFailure).when(fixture.flow).close();
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(credential, null)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(fixture.controller::close).isSameAs(closeFailure);
                release.countDown();
                callback.get(5, TimeUnit.SECONDS);

                assertThatThrownBy(fixture.controller::awaitAuthenticationStopped).isSameAs(closeFailure)
                        .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(persistenceFailure));

                assertThat(delivered).isEmpty();
                assertThat(fixture.controller.getCredential()).isNull();
                assertThat(fixture.scheduler.isTerminated()).isTrue();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void aFatalPersistenceFailureIsRetainedByTheStopWaiter() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture()) {
            var delivered = new CopyOnWriteArrayList<Boolean>();
            fixture.controller.startAuthenticate(delivered::add);
            var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
            fixture.controller.getCredentialManager().getCredentials().add(credential);
            var failure = new LinkageError("credential provider is unavailable");
            doAnswer(invocation -> {
                entered.countDown();
                await(release);
                throw failure;
            }).when(fixture.store).saveCredentials(anyList());
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(credential, null)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                fixture.controller.close();
                release.countDown();
                assertThatThrownBy(() -> callback.get(5, TimeUnit.SECONDS)).hasCause(failure);

                assertThatThrownBy(fixture.controller::awaitAuthenticationStopped).isSameAs(failure);

                assertThat(delivered).isEmpty();
                assertThat(fixture.controller.getCredential()).isNull();
                assertThat(fixture.scheduler.isTerminated()).isTrue();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void stoppingWithACancelledCredentialWriteDoesNotReportAStorageFailure() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture()) {
            var delivered = new CopyOnWriteArrayList<Boolean>();
            fixture.controller.startAuthenticate(delivered::add);
            var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
            fixture.controller.getCredentialManager().getCredentials().add(credential);
            doAnswer(invocation -> {
                entered.countDown();
                await(release);
                throw new java.util.concurrent.CancellationException("credential session has ended");
            }).when(fixture.store).saveCredentials(anyList());
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(credential, null)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                fixture.controller.close();
                release.countDown();
                callback.get(5, TimeUnit.SECONDS);

                assertThatCode(fixture.controller::awaitAuthenticationStopped).doesNotThrowAnyException();

                assertThat(delivered).isEmpty();
                assertThat(fixture.controller.getCredential()).isNull();
                assertThat(fixture.scheduler.isTerminated()).isTrue();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void aPersistenceFailureAfterCloseIsRetainedByTheStopWaiter() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture()) {
            var delivered = new CopyOnWriteArrayList<Boolean>();
            fixture.controller.startAuthenticate(delivered::add);
            var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
            fixture.controller.getCredentialManager().getCredentials().add(credential);
            var failure = new com.github.k7t3.tcv.app.secure.KeyManagerException("credential persistence failed");
            doAnswer(invocation -> {
                entered.countDown();
                await(release);
                throw failure;
            }).when(fixture.store).saveCredentials(anyList());
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(credential, null)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                fixture.controller.close();
                release.countDown();
                callback.get(5, TimeUnit.SECONDS);

                assertThatThrownBy(fixture.controller::awaitAuthenticationStopped).isSameAs(failure);

                assertThat(delivered).isEmpty();
                assertThat(fixture.controller.getCredential()).isNull();
                assertThat(fixture.scheduler.isTerminated()).isTrue();
                verify(fixture.store).saveCredentials(List.of(credential));
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void stopRequestsReturnBeforeTheRunningCallbackButAwaitWaitsForItsExit() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            fixture.controller.startAuthenticate(result -> { entered.countDown(); await(release); });
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(null, null)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                fixture.controller.close();
                assertThat(fixture.scheduler.isShutdown()).isTrue();
                var completed = workers.submit(fixture.controller::awaitAuthenticationStopped);
                assertThat(fixture.scheduler.awaiting.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(completed.isDone()).isFalse();
                release.countDown();
                callback.get(5, TimeUnit.SECONDS);
                completed.get(5, TimeUnit.SECONDS);
                assertThat(fixture.scheduler.isTerminated()).isTrue();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void callbackCanRequestStopButCannotWaitForItsOwnTermination() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.controller.startAuthenticate(result -> {
                fixture.controller.close();
                assertThatThrownBy(fixture.controller::awaitAuthenticationStopped).isInstanceOf(IllegalStateException.class);
            });
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(null, null)));
            callback.get(5, TimeUnit.SECONDS);
            fixture.controller.awaitAuthenticationStopped();
            assertThat(fixture.scheduler.isTerminated()).isTrue();
            verify(fixture.flow).close();
        }
    }

    @Test
    void closingDuringCredentialPersistenceDoesNotDeliverALateSuccess() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture()) {
            var delivered = new CopyOnWriteArrayList<Boolean>();
            fixture.controller.startAuthenticate(delivered::add);
            var credential = new OAuth2Credential("twitch", "access", "refresh", "123", "user", 3600, List.of());
            fixture.controller.getCredentialManager().getCredentials().add(credential);
            doAnswer(invocation -> { entered.countDown(); await(release); return null; }).when(fixture.store).saveCredentials(anyList());
            var callback = fixture.scheduler.submit(() -> fixture.callback.accept(new DeviceTokenResponse(credential, null)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                fixture.controller.close();
                release.countDown();
                callback.get(5, TimeUnit.SECONDS);
                fixture.controller.awaitAuthenticationStopped();
                assertThat(delivered).isEmpty();
                assertThat(fixture.controller.getCredential()).isNull();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    @Test
    void aFlowCloseFailureStillStopsAndWaitsForTheOwnedScheduler() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var failure = new IllegalStateException("flow could not close");
            doThrow(failure).when(fixture.flow).close();
            var running = fixture.scheduler.submit(() -> { entered.countDown(); await(release); });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(fixture.controller::close).isSameAs(failure);
                var completed = workers.submit(fixture.controller::awaitAuthenticationStopped);
                assertThat(fixture.scheduler.awaiting.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(completed.isDone()).isFalse();
                release.countDown();
                running.get(5, TimeUnit.SECONDS);
                assertThatThrownBy(() -> completed.get(5, TimeUnit.SECONDS)).hasCause(failure);
                assertThat(fixture.scheduler.isTerminated()).isTrue();
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    private static void await(CountDownLatch latch) {
        while (true) {
            try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); return; }
            catch (InterruptedException ignored) { }
        }
    }

    private static final class TrackingScheduler extends ScheduledThreadPoolExecutor {
        final CountDownLatch awaiting = new CountDownLatch(1);
        TrackingScheduler() { super(1, Thread.ofVirtual().factory()); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            awaiting.countDown();
            return super.awaitTermination(timeout, unit);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TrackingScheduler scheduler = new TrackingScheduler();
        final TwitchIdentityProvider identity = mock(TwitchIdentityProvider.class);
        final DeviceFlowController flow = mock(DeviceFlowController.class);
        final CredentialStore store = mock(CredentialStore.class);
        final CredentialController controller;
        Consumer<DeviceTokenResponse> callback;
        Fixture() {
            when(identity.getProviderName()).thenReturn("twitch");
            when(store.loadCredentials()).thenReturn(new ArrayList<>());
            when(flow.startOAuth2DeviceAuthorizationGrantType(any(), anyCollection(), any())).thenAnswer(invocation -> {
                callback = invocation.getArgument(2);
                return new DeviceAuthorization("device", "CODE", "https://example.test", 60, 1,
                        "https://example.test/complete", Map.of(), Instant.EPOCH);
            });
            controller = new CredentialController(store, identity, flow, scheduler);
        }
        @Override public void close() {
            try { controller.close(); }
            finally { scheduler.shutdownNow(); scheduler.close(); }
        }
    }
}
