package com.github.k7t3.tcv.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class FXTaskCompletionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observationCancellationDoesNotInterruptThePublishingThreadWhileOrdinaryTasksStillCan(boolean observation, FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        Callable<String> publication = () -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("unreleased publication");
            } catch (InterruptedException cancelled) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return "result";
        };
        var task = observation ? FXTask.observation(publication) : FXTask.task(publication);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var finished = executor.submit(() -> {
                task.run();
                return Thread.currentThread().isInterrupted();
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> task.cancel(true));
                release.countDown();
                assertThat(finished.get(5, TimeUnit.SECONDS)).isEqualTo(!observation);
                assertThat(interrupted.get()).isEqualTo(!observation);
                assertThatThrownBy(task::get).isInstanceOf(CancellationException.class);
            } finally { release.countDown(); }
        }
    }

    @Test
    void resultAndOriginalFailureAreObservableWhileFxEventsCannotRun(FxRobot robot) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            robot.interact(() -> {
                var success = FXTask.task(() -> "finished");
                var failure = new IllegalStateException("leave failed");
                var failed = FXTask.task(() -> { throw failure; });
                executor.execute(success);
                executor.execute(failed);
                assertThat(success.completion().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join()).isEqualTo("finished");
                assertThatThrownBy(() -> failed.completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                        .hasCause(failure);
            });
        }
    }

    @Test
    void cancellingAnObserverDoesNotCancelTheTask(FxRobot robot) throws Exception {
        var task = FXTask.task(() -> "finished");
        var observer = task.completion().toCompletableFuture();
        observer.cancel(false);
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.execute(task);
            assertThat(task.get(5, TimeUnit.SECONDS)).isEqualTo("finished");
            assertThat(task.completion().toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("finished");
            assertThat(task.isCancelled()).isFalse();
        }
    }

    @Test
    void cancellationReportsFutureTerminationBeforeAnUninterruptibleCallReturns(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var task = FXTask.task(() -> {
            entered.countDown();
            try { release.await(); }
            finally { returned.countDown(); }
            return null;
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                executor.execute(task);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> task.cancel(false));
                assertThatThrownBy(() -> task.completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(CancellationException.class);
                assertThat(returned.getCount()).isOne();
            } finally {
                release.countDown();
            }
        }
        assertThat(returned.getCount()).isZero();
    }
}
