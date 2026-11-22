package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.app.secure.KeyManagerException;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.twitch4j.common.exception.UnauthorizedException;
import com.github.twitch4j.helix.domain.Clip;
import com.github.twitch4j.helix.domain.ClipList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwitchAPITest {

    @Test
    void aKeyStorageFailureAfter401DoesNotInvalidateTheSessionOrDeleteItsStoredCredential() {
        var own = new OAuth2Credential("twitch", "own", "refresh", "123", "User", 3600, List.of());
        var rotated = new OAuth2Credential("twitch", "rotated", "refresh-next", "123", "User", 3600, List.of());
        var backing = mock(CredentialStore.class);
        when(backing.snapshot()).thenReturn(new CredentialStore.Snapshot("1", List.of(own)));
        var failure = new KeyManagerException("key store is unavailable");
        when(backing.compareAndSet(any(), any(), any())).thenThrow(failure);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(unauthorized);
        try (var twitch = new Twitch(own, backing, client);
             var identities = mockConstruction(TwitchIdentityProvider.class, (identity, context) -> {
                 when(identity.getProviderName()).thenReturn("twitch");
                 when(identity.getProviderType()).thenReturn("oauth2");
                 when(identity.refreshCredential(any())).thenReturn(Optional.of(rotated));
             })) {
            var api = twitch.getTwitchAPI();
            assertThatThrownBy(() -> api.getClips(List.of("clip"))).isInstanceOf(RuntimeException.class).hasCause(failure);
            assertThat(twitch.hasInvalidCredentials()).isFalse();
            assertThat(twitch.getAccessToken()).isEqualTo("own");
            assertThat(twitch.getCredentialStore().loadCredentials()).containsExactly(own);
            verify(client, Mockito.never()).close();
            verify(backing).compareAndSet(any(), any(), any());
            assertThat(identities.constructed()).hasSize(1);
            verify(identities.constructed().getFirst(), Mockito.never()).revokeCredential(any());
        }
    }

    @Test
    void endingASessionAfterACommittedRefreshProducesCancellationInsteadOfASecondCredentialWrite() throws Exception {
        var own = new OAuth2Credential("twitch", "own", "refresh", "123", "User", 3600, List.of());
        var refreshed = new OAuth2Credential("twitch", "rotated", "refresh-next", "123", "User", 3600, List.of());
        var backing = mock(CredentialStore.class);
        when(backing.snapshot()).thenReturn(new CredentialStore.Snapshot("1", List.of(own)));
        when(backing.compareAndSet(any(), any(), any())).thenReturn(Optional.of(
                new CredentialStore.Snapshot("2", List.of(refreshed))));
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(unauthorized);
        try (var twitch = new Twitch(own, backing, client);
             var controllers = mockConstruction(CredentialController.class, (controller, context) ->
                     when(controller.refreshToken()).thenAnswer(invocation -> {
                         twitch.getCredentialStore().saveCredentials(List.of(refreshed));
                         twitch.close();
                         return refreshed;
                     }))) {
            var api = twitch.getTwitchAPI();
            assertThatThrownBy(() -> api.getClips(List.of("clip"))).isInstanceOf(CancellationException.class);
            assertThat(twitch.getAccessToken()).isEqualTo("rotated");
            verify(helix).getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(client).close();
        }
    }

    @Test
    void invalidationDoesNotWaitForAMonitorWhileHoldingTheLockThatMonitorNeeds() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        var monitorRequested = new CountDownLatch(1);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            if (Thread.currentThread().getName().startsWith("Invalidation-Monitor")) monitorRequested.countDown();
            throw unauthorized;
        });
        var scheduler = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("Invalidation-Monitor").factory());
        var api = new TwitchAPI(twitch, scheduler);
        var refreshing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completion = new CompletableFuture<Throwable>();
        var external = Thread.ofVirtual().start(() -> {
            try (var controllers = mockConstruction(CredentialController.class, (controller, context) ->
                    when(controller.refreshToken()).thenAnswer(invocation -> {
                        refreshing.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("refresh was not released");
                        return null;
                    }))) {
                try { api.getClips(List.of("external")); completion.complete(null); }
                catch (Throwable failure) { completion.complete(failure); }
            }
        });
        try {
            assertThat(refreshing.await(5, TimeUnit.SECONDS)).isTrue();
            var monitor = scheduler.submit(() -> api.getClips(List.of("monitor")));
            assertThat(monitorRequested.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(completion.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(InvalidCredentialException.class);
            assertThatThrownBy(() -> monitor.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(InvalidCredentialException.class);
            assertThat(scheduler.isTerminated()).isTrue();
        } finally {
            release.countDown();
            scheduler.shutdownNow();
            external.interrupt();
            if (completion.isDone()) api.close();
        }
    }

    @Test
    void failedRefreshInvalidatesTheOwnedSessionAndStopsMonitoringWithoutDirectLogout() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(unauthorized);
        var scheduler = new ScheduledThreadPoolExecutor(1);
        try (var controllers = mockConstruction(CredentialController.class);
             var api = new TwitchAPI(twitch, scheduler)) {
            assertThatThrownBy(() -> api.getClips(List.of("clip")))
                    .isInstanceOf(InvalidCredentialException.class);
            verify(twitch).invalidateCredentials();
            Mockito.verify(twitch, Mockito.never()).logout();
            assertThat(scheduler.isShutdown()).isTrue();
            assertThatThrownBy(() -> api.getClips(List.of("clip")))
                    .isInstanceOf(InvalidCredentialException.class);
            verify(helix).getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertThat(controllers.constructed()).hasSize(1);
            verify(controllers.constructed().getFirst()).close();
        }
    }

    @Test
    void cancelledRefreshStaysCancelledAndDoesNotInvalidateOrLogout() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(unauthorized);
        try (var controllers = mockConstruction(CredentialController.class, (controller, context) ->
                when(controller.refreshToken()).thenThrow(new CancellationException()));
             var api = new TwitchAPI(twitch)) {
            assertThatThrownBy(() -> api.getClips(List.of("clip"))).isInstanceOf(CancellationException.class);
            Mockito.verify(twitch, Mockito.never()).invalidateCredentials();
            Mockito.verify(twitch, Mockito.never()).logout();
        }
    }

    @Test
    void externalCloseWaitsUntilAnInterruptedMonitorFinishesItsCleanup() throws Exception {
        var scheduler = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().factory());
        var api = new TwitchAPI(mock(Twitch.class), scheduler);
        var started = new CountDownLatch(1);
        var stopRequested = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var running = scheduler.submit(() -> {
                    var interrupted = false;
                    started.countDown();
                    try {
                        while (release.getCount() != 0) {
                            try {
                                if (!release.await(5, TimeUnit.SECONDS)) {
                                    throw new AssertionError("monitor cleanup was not released");
                                }
                            } catch (InterruptedException cancellation) {
                                interrupted = true;
                                stopRequested.countDown();
                            }
                        }
                    } finally {
                        if (interrupted) Thread.currentThread().interrupt();
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = closer.submit(() -> { api.close(); return null; });
                assertThat(stopRequested.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);

                release.countDown();
                closing.get(5, TimeUnit.SECONDS);
                running.get(5, TimeUnit.SECONDS);
                assertThat(scheduler.isTerminated()).isTrue();
            } finally {
                release.countDown();
                api.close();
            }
        }
    }

    @Test
    void monitorCanRequestItsOwnShutdownWithoutWaitingForItself() throws Exception {
        var monitorThread = new AtomicReference<Thread>();
        var scheduler = new ScheduledThreadPoolExecutor(1, task -> {
            var thread = Thread.ofPlatform().daemon().unstarted(task);
            monitorThread.set(thread);
            return thread;
        }) {
            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                // 自己待機は永久に完了しないため、その呼び出しを検出してテストを終了可能にする。
                if (Thread.currentThread() == monitorThread.get()) {
                    throw new AssertionError("monitor is waiting for its own termination");
                }
                return super.awaitTermination(timeout, unit);
            }
        };
        var api = new TwitchAPI(mock(Twitch.class), scheduler);
        try {
            var closing = scheduler.submit(() -> {
                api.close();
                return null;
            });

            assertThat(closing.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(scheduler.isShutdown()).isTrue();
        } finally {
            scheduler.shutdownNow();
            api.close();
        }
    }

    @Test
    void requestTimeoutIsRetriedBeforeReturningClips() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var timeout = new java.util.concurrent.CompletionException(new TimeoutException("request timed out"));
        var clip = mock(Clip.class);
        var response = mock(ClipList.class);
        when(response.getData()).thenReturn(List.of(clip));
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(timeout).thenReturn(response);

        try (var api = new TwitchAPI(twitch)) {
            assertThat(api.getClips(List.of("clip-id"))).containsExactly(clip);
        }
    }

    @Test
    void permanentlyUnavailableServiceStopsAfterFiveAttempts() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var timeout = new java.util.concurrent.CompletionException(new TimeoutException("upstream remains unavailable"));
        var attempts = new AtomicInteger();
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() > 5) throw new AssertionError("request retried indefinitely");
            throw timeout;
        });

        try (var api = new TwitchAPI(twitch)) {
            assertThatThrownBy(() -> api.getClips(List.of("clip-id")))
                    .isInstanceOf(RuntimeException.class).hasCause(timeout);

            assertThat(attempts.get()).isEqualTo(5);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4})
    void cancelledRequestPreservesItsInterruption(int precedingTransientFailures) throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var interrupted = new java.util.concurrent.CompletionException(new InterruptedException("request cancelled"));
        var timeout = new java.util.concurrent.CompletionException(new TimeoutException("temporary upstream timeout"));
        var failures = new AtomicInteger();
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            if (failures.getAndIncrement() < precedingTransientFailures) throw timeout;
            throw interrupted;
        });

        try (var api = new TwitchAPI(twitch); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = worker.submit(() -> {
                if (precedingTransientFailures < 0) Thread.currentThread().interrupt();

                assertThatThrownBy(() -> api.getClips(List.of("clip-id")))
                        .isInstanceOf(RuntimeException.class);

                return Thread.currentThread().isInterrupted();
            });

            assertThat(request.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void repeatedTransientFailuresDoNotConsumeAllRequestSlots() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var timeout = new java.util.concurrent.CompletionException(new TimeoutException("temporary upstream timeout"));
        var clip = mock(Clip.class);
        var response = mock(ClipList.class);
        when(response.getData()).thenReturn(List.of(clip));
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(timeout, timeout, timeout, timeout).thenReturn(response);

        try (var api = new TwitchAPI(twitch); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = worker.submit(() -> api.getClips(List.of("clip-id")));
            try {
                assertThat(request.get(5, TimeUnit.SECONDS)).containsExactly(clip);
            } finally {
                request.cancel(true);
            }
        }
    }

    @Test
    void unauthorizedRequestClosesItsCredentialRefresherBeforeReturningClips() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        var clip = mock(Clip.class);
        var response = mock(ClipList.class);
        when(response.getData()).thenReturn(List.of(clip));
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(unauthorized).thenReturn(response);
        var credential = mock(OAuth2Credential.class);

        try (var controllers = mockConstruction(CredentialController.class,
                (controller, context) -> when(controller.refreshToken()).thenReturn(credential));
             var api = new TwitchAPI(twitch)) {
            var clips = api.getClips(List.of("clip-id"));

            assertThat(clips).containsExactly(clip);
            assertThat(controllers.constructed()).hasSize(1);
            verify(controllers.constructed().getFirst()).close();
        }
    }
}
