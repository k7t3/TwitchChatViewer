package com.github.k7t3.tcv.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.net.URI;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class BrowserActionsTest {
    @Test
    void batchOpeningCapturesItsUrlsBeforeTheSelectionChanges(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            var urls = new ArrayList<>(List.of("https://www.twitch.tv/first", "https://www.twitch.tv/second"));
            robot.interact(() -> { f.result = f.actions.openAll(urls); urls.clear(); urls.add("https://www.twitch.tv/replacement"); });
            assertThat(f.opened).isEmpty(); f.runNext();
            assertThat(f.result.get(5, TimeUnit.SECONDS)).isEqualTo(true);
            assertThat(f.opened).containsExactly(URI.create("https://www.twitch.tv/first"), URI.create("https://www.twitch.tv/second"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void endingTheScopeCancelsQueuedOpenings(boolean close, FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.result = f.actions.open("https://www.twitch.tv/first");
                if (close) f.actions.close(); else f.actions.cancelAll();
                assertThat(f.result.isCancelled()).isTrue();
                if (close) assertThatThrownBy(() -> f.actions.open("https://www.twitch.tv/next")).isInstanceOf(IllegalStateException.class);
            });
            f.runNext(); assertThat(f.opened).isEmpty();
        }
    }

    @Test
    void cancellationAfterTheWorkerFinishesStillSuppressesQueuedSuccess(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.result = f.actions.open("https://www.twitch.tv/first");
                f.runNext();
                f.actions.close();
            });
            robot.interact(() -> assertThat(f.result.isCancelled()).isTrue());
        }
    }

    @Test
    void failureKeepsItsCauseAndDoesNotPoisonTheNextOpening(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            var failure = new IOException("browser unavailable");
            f.opener = uri -> { throw failure; };
            robot.interact(() -> f.result = f.actions.open("https://www.twitch.tv/first")); f.runNext();
            assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> {
                assertThat(f.errors).containsExactly(failure);
                f.opener = uri -> false;
                f.result = f.actions.open("https://www.twitch.tv/second");
            });
            f.runNext(); assertThat(f.result.get(5, TimeUnit.SECONDS)).isEqualTo(false);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellingDuringOneOpenStopsTheRemainingBatchAndWaitsForThePhysicalCall(boolean close, FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var f = new Fixture(robot); var worker = Executors.newSingleThreadExecutor()) {
            f.opener = uri -> {
                f.opened.add(uri); entered.countDown();
                for (;;) { try { release.await(); break; } catch (InterruptedException ignored) {} }
                return true;
            };
            robot.interact(() -> f.result = f.actions.openAll(List.of("https://www.twitch.tv/first", "https://www.twitch.tv/second")));
            var running = worker.submit(f::runNext);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> { if (close) f.actions.close(); else f.result.cancel(true); });
                assertThat(f.drains.getFirst().toCompletableFuture().isDone()).isFalse();
                assertThat(f.result.isCancelled()).isTrue();
            } finally { release.countDown(); }
            running.get(5, TimeUnit.SECONDS); f.drains.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(f.opened).containsExactly(URI.create("https://www.twitch.tv/first"));
        } finally { release.countDown(); }
    }

    @Test
    void cancellationInsideTheBrowserAdapterAlsoFinishesItsObserver(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            f.opener = uri -> { throw new CancellationException("desktop operation cancelled"); };
            robot.interact(() -> f.result = f.actions.open("https://www.twitch.tv/first"));
            f.runNext();
            assertThatThrownBy(() -> f.result.get(1, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void executorStartupFailureFinishesTheObserverAndPhysicalDrain(boolean fatal, FxRobot robot) throws Exception {
        Throwable failure = fatal ? new AssertionError("browser executor unavailable") : new RejectedExecutionException("worker closed");
        Executor rejecting = task -> { if (failure instanceof Error error) throw error; throw (RuntimeException) failure; };
        try (var f = new Fixture(robot, rejecting)) {
            robot.interact(() -> f.result = f.actions.open("https://www.twitch.tv/first"));
            if (fatal) assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasRootCause(failure);
            else assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasCause(failure);
            f.drains.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(f.opened).isEmpty();
            robot.interact(() -> assertThat(f.errors).containsExactly(failure));
        }
    }

    @Test
    void aNewScopeCanOpenAnotherUrlBeforeTheOldCancelledJobIsDelivered(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.actions.open("https://www.twitch.tv/old"); f.actions.cancelAll();
                f.result = f.actions.open("https://www.twitch.tv/next");
            });
            f.runNext(); f.runNext();
            assertThat(f.result.get(5, TimeUnit.SECONDS)).isEqualTo(true);
            assertThat(f.opened).containsExactly(URI.create("https://www.twitch.tv/next"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final FxRobot robot;
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final List<URI> opened = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<CompletionStage<Void>> drains = new CopyOnWriteArrayList<>();
        BrowserActions.Opener opener = uri -> { opened.add(uri); return true; };
        final BrowserActions actions;
        FXTask<Boolean> result;
        Fixture(FxRobot robot) { this(robot, null); }
        Fixture(FxRobot robot, Executor executor) {
            this.robot = robot;
            actions = new BrowserActions(executor == null ? queue::add : executor, drains::add, errors::add, uri -> opener.open(uri));
        }
        void runNext() { Objects.requireNonNull(queue.poll(), "expected browser job").run(); }
        @Override public void close() {
            robot.interact(actions::close);
            Runnable task; while ((task = queue.poll()) != null) task.run();
        }
    }
}
