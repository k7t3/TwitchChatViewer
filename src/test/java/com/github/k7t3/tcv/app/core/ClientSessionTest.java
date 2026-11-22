package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ClientSessionTest {
    @Test
    void failureFreezingCredentialsStillReleasesTheDomainAndClient() throws Exception {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var failure = new IllegalStateException("cannot stop credential updates");
        doThrow(failure).when(twitch).freezeCredentials();
        var session = new ClientSession(twitch, channels);

        assertThatThrownBy(session::close).isSameAs(failure);

        verify(channels).clear();
        verify(twitch).close();
        assertThatThrownBy(() -> session.completion().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThatThrownBy(session::close).isSameAs(failure);
        verifyNoMoreInteractions(channels);
    }

    @Test
    void logoutClearsTheDomainBeforeClosingAndRevokingTheSameClient() throws Exception {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var session = new ClientSession(twitch, channels);
        session.requestLogout();

        session.close();
        session.close();

        var order = inOrder(channels, twitch);
        order.verify(channels).clear();
        order.verify(twitch).logout();
        verify(twitch, never()).close();
        session.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void normalShutdownKeepsTheStoredCredential() {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var session = new ClientSession(twitch, channels);

        session.close();

        var order = inOrder(channels, twitch);
        order.verify(channels).clear();
        order.verify(twitch).close();
        verify(twitch, never()).logout();
    }

    @Test
    void failureStillClosesTheClientAndRemainsObservableWithoutRepeatingCleanup() {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var first = new IllegalStateException("domain close failed");
        var second = new IllegalStateException("client close failed");
        doThrow(first).when(channels).clear();
        doThrow(second).when(twitch).close();
        var session = new ClientSession(twitch, channels);

        assertThatThrownBy(session::close).isSameAs(first);
        assertThatThrownBy(session::close).isSameAs(first);

        assertThat(first.getSuppressed()).containsExactly(second);
        verify(channels).clear();
        verify(twitch).close();
        assertThat(session.completion().toCompletableFuture().isCompletedExceptionally()).isTrue();
    }

    @Test
    void anotherCloserWaitsForTheActualResourceCompletion() throws Exception {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); release.await(); return null; }).when(channels).clear();
        var session = new ClientSession(twitch, channels);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(session::close);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(session::close);
                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                verify(twitch, never()).close();
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                verify(twitch).close();
            } finally {
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void cancellingAnObserverCannotCancelTheOwnedCleanup() throws Exception {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var session = new ClientSession(twitch, channels);
        var observer = session.completion().toCompletableFuture();
        observer.cancel(true);

        session.close();

        verify(channels).clear();
        verify(twitch).close();
        session.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void nestedCloseFromItsOwnClientDoesNotWaitForItself() throws Exception {
        var twitch = mock(Twitch.class);
        var session = new ClientSession(twitch, mock(ChannelRepository.class));
        doAnswer(invocation -> { session.close(); return null; }).when(twitch).close();

        var closed = CompletableFuture.runAsync(session::close, action -> Thread.ofVirtual().start(action));

        closed.get(5, TimeUnit.SECONDS);
        verify(twitch).close();
    }
}
