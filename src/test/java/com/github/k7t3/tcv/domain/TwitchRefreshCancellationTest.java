package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.common.exception.UnauthorizedException;
import com.github.twitch4j.helix.domain.ClipList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwitchRefreshCancellationTest {
    @Test
    void cancellingARefreshWaiterEndsOnlyThatRequestBeforeTheActiveRefreshReturns() throws Exception {
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class);
        var helix = mock(HelixClient.class);
        when(twitch.getClient()).thenReturn(client);
        when(client.getHelix()).thenReturn(helix);
        var unauthorized = mock(UnauthorizedException.class);
        var clips = mock(ClipList.class);
        when(clips.getData()).thenReturn(List.of());
        var attempted = ConcurrentHashMap.<Thread>newKeySet();
        var waiterRequested = new CountDownLatch(1);
        when(helix.getClips(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            if (attempted.add(Thread.currentThread())) {
                if (Thread.currentThread().getName().equals("Refresh-Cancel-Waiter")) waiterRequested.countDown();
                throw unauthorized;
            }
            return clips;
        });
        var refreshStarted = new CountDownLatch(1);
        var releaseRefresh = new CountDownLatch(1);
        var ownerResult = new CompletableFuture<Throwable>();
        var waiterResult = new CompletableFuture<Outcome>();
        try (var api = new TwitchAPI(twitch)) {
            var owner = Thread.ofVirtual().name("Refresh-Cancel-Owner").start(() -> {
                try (var controllers = mockConstruction(CredentialController.class, (controller, context) ->
                        when(controller.refreshToken()).thenAnswer(invocation -> {
                            refreshStarted.countDown();
                            if (!releaseRefresh.await(10, TimeUnit.SECONDS)) throw new AssertionError("refresh not released");
                            return new OAuth2Credential("twitch", "synthetic");
                        }))) {
                    api.getClips(List.of("owner"));
                    ownerResult.complete(null);
                } catch (Throwable failure) { ownerResult.complete(failure); }
            });
            Thread waiter = null;
            try {
                assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();
                waiter = Thread.ofVirtual().name("Refresh-Cancel-Waiter").start(() -> {
                    try { api.getClips(List.of("waiter")); waiterResult.complete(new Outcome(null, Thread.currentThread().isInterrupted())); }
                    catch (Throwable failure) { waiterResult.complete(new Outcome(failure, Thread.currentThread().isInterrupted())); }
                });
                assertThat(waiterRequested.await(5, TimeUnit.SECONDS)).isTrue();

                waiter.interrupt();
                var outcome = waiterResult.get(2, TimeUnit.SECONDS);

                assertThat(outcome.failure()).isInstanceOf(CancellationException.class);
                assertThat(outcome.interrupted()).isTrue();
                assertThat(ownerResult).isNotDone();
                verify(twitch, never()).invalidateCredentials();
                verify(twitch, never()).logout();
            } finally {
                releaseRefresh.countDown();
                owner.join(Duration.ofSeconds(5));
                if (waiter != null) waiter.join(Duration.ofSeconds(5));
                assertThat(owner.isAlive()).isFalse();
                if (waiter != null) assertThat(waiter.isAlive()).isFalse();
            }
            assertThat(ownerResult.get(1, TimeUnit.SECONDS)).isNull();
        }
    }

    private record Outcome(Throwable failure, boolean interrupted) {}
}
