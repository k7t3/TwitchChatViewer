package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class AppHelperSessionTest {
    @Test
    void aCredentialFreezeFailureStillDetachesAndFinishesTheRequestedLogout(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var failure = new IllegalStateException("cannot stop credential updates");
        var detached = new java.util.concurrent.atomic.AtomicBoolean();
        var result = new AtomicReference<CompletionStage<Void>>();
        robot.interact(() -> fixture.helper.setTwitch(twitch));
        doThrow(failure).when(twitch).freezeCredentials();
        try {
            robot.interact(() -> result.set(fixture.helper.logoutAsync(() -> detached.set(true))));

            assertThat(detached).isTrue();
            robot.interact(() -> {
                assertThat(fixture.helper.getTwitch()).isNull();
                assertThat(fixture.helper.isAuthorized()).isFalse();
            });
            fixture.runNext();
            assertThatThrownBy(() -> result.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
            verify(domain).clear();
            verify(twitch).logout();
            verify(twitch, never()).close();
        } finally {
            // 修正前に要求の途中で失敗した場合も、公開APIで保留中の切離しを完了させる。
            doNothing().when(twitch).freezeCredentials();
            robot.interact(() -> { if (fixture.helper.getTwitch() != null) fixture.helper.logoutAsync(); });
            Runnable pending;
            while ((pending = fixture.queue.poll()) != null) pending.run();
            try { fixture.helper.close(); }
            catch (RuntimeException | Error closingFailure) { assertThat(closingFailure).isSameAs(failure); }
        }
    }

    @Test
    void endingOneApplicationDoesNotRetireAnotherApplicationsMatchingCredential(FxRobot robot) throws Exception {
        var first = new Fixture();
        var second = new Fixture();
        var previous = mock(Twitch.class);
        var current = mock(Twitch.class);
        when(previous.getAccessToken()).thenReturn("same-token");
        when(current.getAccessToken()).thenReturn("same-token");
        var previousDomain = first.domain(previous);
        var currentDomain = second.domain(current);
        try {
            robot.interact(() -> {
                first.helper.setTwitch(previous);
                first.helper.logoutAsync();
                second.helper.setTwitch(current);
            });
            first.helper.close();
            first.runNext();

            robot.interact(() -> {
                assertThat(second.helper.getTwitch()).isSameAs(current);
                assertThat(second.helper.getChannelRepository()).isSameAs(currentDomain);
                assertThat(second.helper.isAuthorized()).isTrue();
            });
            verify(previousDomain).clear();
            verify(previous).logout();
            verify(current, never()).freezeCredentials();
            verify(current, never()).close();
            verify(currentDomain, never()).clear();
        } finally {
            try { first.helper.close(); }
            finally { second.helper.close(); }
        }
        verify(currentDomain).clear();
        verify(current).close();
    }

    @Test
    void invalidationDuringUiShutdownDoesNotScheduleAnotherSessionEnd(FxRobot robot) {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var invalidated = new AtomicReference<Runnable>();
        doAnswer(invocation -> { invalidated.set(invocation.getArgument(0)); return null; }).when(twitch).onInvalidated(any());
        try {
            robot.interact(() -> {
                fixture.helper.setTwitch(twitch);
                fixture.helper.prepareShutdown();
                invalidated.get().run();
            });
            robot.interact(() -> {
                assertThat(fixture.helper.isAuthorized()).isFalse();
                assertThat(fixture.helper.getTwitch()).isNull();
                assertThat(fixture.queue).isEmpty();
            });
        } finally { fixture.helper.close(); }
        verify(domain).clear();
        verify(twitch).close();
        verify(twitch, never()).logout();
    }

    @Test
    void invalidationDetachesOnlyItsCurrentSessionThenClosesWithoutRemoteRevocation(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var invalidated = new AtomicReference<Runnable>();
        doAnswer(invocation -> { invalidated.set(invocation.getArgument(0)); return null; }).when(twitch).onInvalidated(any());
        try {
            robot.interact(() -> fixture.helper.setTwitch(twitch));
            assertThat(invalidated.get()).isNotNull();
            invalidated.get().run();
            robot.interact(() -> {
                assertThat(fixture.helper.isAuthorized()).isFalse();
                assertThat(fixture.helper.getTwitch()).isNull();
            });
            fixture.runNext();
            verify(twitch, atLeastOnce()).freezeCredentials();
            var order = inOrder(domain, twitch);
            order.verify(domain).clear();
            order.verify(twitch).close();
            verify(twitch, never()).logout();
        } finally { fixture.helper.close(); }
    }

    @Test
    void delayedInvalidationCannotDetachANewerSession(FxRobot robot) {
        var fixture = new Fixture();
        var previous = mock(Twitch.class);
        var current = mock(Twitch.class);
        fixture.domain(previous);
        fixture.domain(current);
        var invalidated = new AtomicReference<Runnable>();
        doAnswer(invocation -> { invalidated.set(invocation.getArgument(0)); return null; }).when(previous).onInvalidated(any());
        try {
            robot.interact(() -> fixture.helper.setTwitch(previous));
            assertThat(invalidated.get()).isNotNull();
            robot.interact(() -> {
                invalidated.get().run();
                fixture.helper.setTwitch(current);
            });
            robot.interact(() -> {
                assertThat(fixture.helper.getTwitch()).isSameAs(current);
                assertThat(fixture.helper.isAuthorized()).isTrue();
            });
            verify(current, never()).freezeCredentials();
        } finally { fixture.helper.close(); }
    }

    @Test
    void tokenRotationDuringAnEndingSessionCannotEvadeItsRevocationGate(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var previous = mock(Twitch.class);
        var stale = mock(Twitch.class);
        when(previous.getAccessToken()).thenReturn("old");
        var tokens = new AtomicReference<>(Set.of("old"));
        when(previous.credentialTokens()).thenAnswer(invocation -> tokens.get());
        when(stale.getAccessToken()).thenReturn("rotated");
        fixture.domain(previous);
        try {
            robot.interact(() -> {
                fixture.helper.setTwitch(previous);
                fixture.helper.logoutAsync();
                tokens.set(Set.of("old", "rotated"));
                assertThatThrownBy(() -> fixture.helper.setTwitch(stale)).isInstanceOf(IllegalStateException.class);
            });
            fixture.runNext();
            robot.interact(() -> assertThatThrownBy(() -> fixture.helper.setTwitch(stale)).isInstanceOf(IllegalStateException.class));
        } finally { fixture.helper.close(); }
    }

    @Test
    void concurrentApplicationCloseCannotOvertakeTheViewDetachment(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var detaching = new CountDownLatch(1);
        var detached = new CountDownLatch(1);
        var cleared = new CountDownLatch(1);
        doAnswer(invocation -> { cleared.countDown(); return null; }).when(domain).clear();
        robot.interact(() -> fixture.helper.setTwitch(twitch));
        Platform.runLater(() -> fixture.helper.logoutAsync(() -> {
            detaching.countDown();
            try {
                if (!detached.await(5, TimeUnit.SECONDS)) throw new AssertionError("view detachment was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        }));
        try (var closers = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(detaching.await(5, TimeUnit.SECONDS)).isTrue();
            var closing = closers.submit(fixture.helper::close);
            try {
                assertThat(cleared.await(100, TimeUnit.MILLISECONDS)).isFalse();
                detached.countDown();
                closing.get(5, TimeUnit.SECONDS);
                robot.interact(() -> {});
                verify(domain).clear();
                verify(twitch).logout();
            } finally {
                detached.countDown();
                closing.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void logoutDetachesViewsOnFxBeforeReleasingItsDomainAndClientOnTheWorker(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var events = new ArrayList<String>();
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            events.add("domain");
            return null;
        }).when(domain).clear();
        doAnswer(invocation -> { events.add("logout"); return null; }).when(twitch).logout();
        var result = new AtomicReference<CompletionStage<Void>>();
        try {
            robot.interact(() -> {
                fixture.helper.setTwitch(twitch);
                assertThat(fixture.helper.getChannelRepository()).isSameAs(domain);
                result.set(fixture.helper.logoutAsync(() -> {
                    assertThat(Platform.isFxApplicationThread()).isTrue();
                    assertThat(fixture.helper.isAuthorized()).isFalse();
                    events.add("detach");
                    verify(domain, never()).clear();
                }));
                assertThat(result.get().toCompletableFuture().isDone()).isFalse();
                assertThat(events).containsExactly("detach");
            });
            fixture.runNext();
            result.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(events).containsExactly("detach", "domain", "logout");
            verify(twitch, never()).close();
        } finally {
            fixture.helper.close();
        }
    }

    @Test
    void anEndingSessionCannotCloseTheClientAdoptedByALaterLogin(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var previous = mock(Twitch.class);
        var current = mock(Twitch.class);
        var oldDomain = fixture.domain(previous);
        var newDomain = fixture.domain(current);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); release.await(); return null; }).when(oldDomain).clear();
        var result = new AtomicReference<CompletionStage<Void>>();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { fixture.helper.setTwitch(previous); result.set(fixture.helper.logoutAsync()); });
            var ending = workers.submit(fixture.queue.remove());
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    fixture.helper.setTwitch(current);
                    assertThat(fixture.helper.getChannelRepository()).isSameAs(newDomain);
                    assertThat(fixture.helper.getTwitch()).isSameAs(current);
                });
                release.countDown();
                ending.get(5, TimeUnit.SECONDS);
                result.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
                verify(previous).logout();
                verify(current, never()).close();
                verify(newDomain, never()).clear();
            } finally {
                release.countDown();
                ending.get(5, TimeUnit.SECONDS);
            }
        } finally {
            fixture.helper.close();
        }
        verify(newDomain).clear();
        verify(current).close();
    }

    @Test
    void applicationCloseKeepsOwnershipEvenIfAQueuedLogoutWasNeverRunOrItsObserverWasCancelled(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var result = new AtomicReference<CompletionStage<Void>>();
        robot.interact(() -> { fixture.helper.setTwitch(twitch); result.set(fixture.helper.logoutAsync()); });
        result.get().toCompletableFuture().cancel(true);

        fixture.helper.close();
        fixture.runNext();

        verify(domain).clear();
        verify(twitch).logout();
        result.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void rejectedSubmissionUsesAnOwnedFallbackAndApplicationCloseWaitsForIt(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        when(fixture.worker.getExecutor()).thenReturn(action -> { throw new RejectedExecutionException("worker closed"); });
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); release.await(); return null; }).when(domain).clear();
        var result = new AtomicReference<CompletionStage<Void>>();
        try (var closers = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { fixture.helper.setTwitch(twitch); result.set(fixture.helper.logoutAsync()); });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var closing = closers.submit(fixture.helper::close);
            try {
                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                robot.interact(() -> assertThat(fixture.helper.isAuthorized()).isFalse());
                release.countDown();
                closing.get(5, TimeUnit.SECONDS);
                result.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
                verify(twitch).logout();
            } finally {
                release.countDown();
                closing.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void aViewFailureDoesNotLoseDomainCleanupAndEveryFailureRemainsObservable(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var twitch = mock(Twitch.class);
        var domain = fixture.domain(twitch);
        var viewFailure = new IllegalStateException("view failed");
        var domainFailure = new IllegalStateException("domain failed");
        var clientFailure = new IllegalStateException("client failed");
        doThrow(domainFailure).when(domain).clear();
        doThrow(clientFailure).when(twitch).logout();
        var result = new AtomicReference<CompletionStage<Void>>();
        robot.interact(() -> {
            fixture.helper.setTwitch(twitch);
            result.set(fixture.helper.logoutAsync(() -> { throw viewFailure; }));
        });
        fixture.runNext();

        assertThatThrownBy(() -> result.get().toCompletableFuture().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCause(viewFailure);
        assertThat(viewFailure.getSuppressed()).containsExactly(domainFailure, clientFailure);
        assertThatThrownBy(fixture.helper::close).isSameAs(viewFailure);
        verify(domain).clear();
        verify(twitch).logout();
    }

    @Test
    void aCredentialScheduledForRevocationCannotBeAdoptedAgainEvenAfterItsOldSessionEnds(FxRobot robot) throws Exception {
        var fixture = new Fixture();
        var previous = mock(Twitch.class);
        var stale = mock(Twitch.class);
        var current = mock(Twitch.class);
        when(previous.getAccessToken()).thenReturn("old-token");
        when(stale.getAccessToken()).thenReturn("old-token");
        when(current.getAccessToken()).thenReturn("new-token");
        fixture.domain(previous);
        fixture.domain(current);
        try {
            robot.interact(() -> {
                fixture.helper.setTwitch(previous);
                fixture.helper.logoutAsync();
                assertThatThrownBy(() -> fixture.helper.setTwitch(stale)).isInstanceOf(IllegalStateException.class);
            });
            fixture.runNext();
            robot.interact(() -> {
                assertThatThrownBy(() -> fixture.helper.setTwitch(stale)).isInstanceOf(IllegalStateException.class);
                fixture.helper.setTwitch(current);
                assertThat(fixture.helper.getTwitch()).isSameAs(current);
            });
            verify(stale, never()).close();
        } finally {
            fixture.helper.close();
            stale.close();
        }
    }

    @Test
    void aClosedApplicationCannotAdoptAnotherClient(FxRobot robot) {
        var fixture = new Fixture();
        fixture.helper.close();
        var twitch = mock(Twitch.class);

        robot.interact(() -> assertThatThrownBy(() -> fixture.helper.setTwitch(twitch)).isInstanceOf(IllegalStateException.class));

        verifyNoInteractions(twitch);
    }

    private static final class Fixture {
        final TaskWorker worker = mock(TaskWorker.class);
        final EventPublishers publishers = mock(EventPublishers.class);
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final Map<Twitch, ChannelRepository> domains = new IdentityHashMap<>();
        final AppHelper helper;

        Fixture() {
            when(worker.getExecutor()).thenReturn(queue::add);
            helper = new AppHelper(worker, publishers, domains::get, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        }

        ChannelRepository domain(Twitch twitch) {
            var domain = mock(ChannelRepository.class);
            domains.put(twitch, domain);
            return domain;
        }

        void runNext() throws InterruptedException {
            var action = queue.poll(5, TimeUnit.SECONDS);
            assertThat(action).isNotNull();
            action.run();
        }
    }
}
