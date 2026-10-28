package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ClientSessionReleaseStartupTest {
    @ParameterizedTest
    @ValueSource(strings = {"runtime", "error", "rejected"})
    void aWorkerStartupFailureStillReturnsTheLogoutStageAndKeepsActualReleaseOwned(String mode, FxRobot robot) throws Exception {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var detached = new AtomicBoolean();
        var cleanupOnFx = new AtomicBoolean(true);
        var stage = new AtomicReference<CompletionStage<Void>>();
        var startupThrown = new AtomicReference<Throwable>();
        doAnswer(call -> {
            cleanupOnFx.set(Platform.isFxApplicationThread());
            entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release session cleanup");
            return null;
        }).when(channels).clear();
        var sessions = new ClientSessions(action -> { failToStart(mode); },
                Platform::runLater, client -> channels, () -> {});
        try (var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> {
                    sessions.adopt(twitch);
                    try { stage.set(sessions.logoutAsync(() -> detached.set(true))); }
                    catch (RuntimeException | Error failure) { startupThrown.set(failure); }
                });

                assertThat(startupThrown.get()).as("logout returns an observation of the owned fallback release").isNull();

                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(detached).isTrue();
                assertThat(cleanupOnFx).isFalse();
                robot.interact(() -> {
                    assertThat(sessions.getTwitch()).isNull();
                    assertThat(sessions.isAuthorized()).isFalse();
                });
                assertThat(stage.get().toCompletableFuture().isDone()).isFalse();
                var closingEntered = new CountDownLatch(1);
                var closing = closer.submit(() -> {
                    closingEntered.countDown();
                    sessions.close();
                });
                assertThat(closingEntered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                verify(twitch, never()).logout();
                finish.countDown();
                stage.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);
                verify(channels).clear();
                verify(twitch).logout();
                verify(twitch, never()).close();
            } finally { finish.countDown(); }
        } finally { sessions.close(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"replace", "invalidate"})
    void endingASessionWithoutLogoutStillOwnsFallbackCleanup(String mode, FxRobot robot) throws Exception {
        var previous = mock(Twitch.class);
        var current = mock(Twitch.class);
        var previousDomain = mock(ChannelRepository.class);
        var currentDomain = mock(ChannelRepository.class);
        var invalidated = new AtomicReference<Runnable>();
        doAnswer(call -> { invalidated.set(call.getArgument(0)); return null; }).when(previous).onInvalidated(any());
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var cleanupOnFx = new AtomicBoolean(true);
        doAnswer(call -> {
            cleanupOnFx.set(Platform.isFxApplicationThread());
            entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release previous session");
            return null;
        }).when(previousDomain).clear();
        var sessions = new ClientSessions(action -> { throw new AssertionError("executor startup failed"); },
                Platform::runLater, client -> client == previous ? previousDomain : currentDomain, () -> {});
        try (var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> {
                    sessions.adopt(previous);
                    if (mode.equals("replace")) sessions.adopt(current);
                    else invalidated.get().run();
                });
                robot.interact(() -> {
                    assertThat(sessions.getTwitch()).isSameAs(mode.equals("replace") ? current : null);
                    assertThat(sessions.isAuthorized()).isEqualTo(mode.equals("replace"));
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(cleanupOnFx).isFalse();
                verify(previous, never()).close();
                verify(currentDomain, never()).clear();
                verify(current, never()).close();
                var closingEntered = new CountDownLatch(1);
                var closing = closer.submit(() -> {
                    closingEntered.countDown();
                    sessions.close();
                });
                assertThat(closingEntered.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                finish.countDown();
                closing.get(5, TimeUnit.SECONDS);
                verify(previousDomain).clear();
                verify(previous).close();
                verify(previous, never()).logout();
                if (mode.equals("replace")) {
                    verify(currentDomain).clear();
                    verify(current).close();
                } else {
                    verify(currentDomain, never()).clear();
                    verify(current, never()).close();
                }
            } finally { finish.countDown(); }
        } finally { sessions.close(); }
    }

    @Test
    void fallbackReleaseFailuresRemainInTheLogoutStageAndApplicationClose(FxRobot robot) throws Exception {
        var twitch = mock(Twitch.class);
        var channels = mock(ChannelRepository.class);
        var domainFailure = new IllegalStateException("domain release failed");
        var sdkFailure = new AssertionError("SDK release failed");
        doThrow(domainFailure).when(channels).clear();
        doThrow(sdkFailure).when(twitch).logout();
        var sessions = new ClientSessions(action -> { throw new IllegalStateException("executor startup failed"); },
                Platform::runLater, client -> channels, () -> {});
        var stage = new AtomicReference<CompletionStage<Void>>();
        try {
            robot.interact(() -> {
                sessions.adopt(twitch);
                stage.set(sessions.logoutAsync(() -> {}));
            });

            assertThatThrownBy(() -> stage.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(domainFailure);

            assertThat(domainFailure.getSuppressed()).containsExactly(sdkFailure);
            assertThatThrownBy(sessions::close).isSameAs(domainFailure);
            assertThatThrownBy(sessions::close).isSameAs(domainFailure);
            verify(channels).clear();
            verify(twitch).logout();
            verify(twitch, never()).close();
        } finally {
            try { sessions.close(); }
            catch (RuntimeException | Error failure) { assertThat(failure).isSameAs(domainFailure); }
        }
    }

    private static void failToStart(String mode) {
        if (mode.equals("error")) throw new AssertionError("executor startup failed");
        if (mode.equals("rejected")) throw new RejectedExecutionException("executor startup failed");
        throw new IllegalStateException("executor startup failed");
    }
}
