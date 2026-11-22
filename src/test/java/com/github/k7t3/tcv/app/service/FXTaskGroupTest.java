package com.github.k7t3.tcv.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class FXTaskGroupTest {
    @Test
    void aStarterCanMutateTheOriginalCollectionWithoutSkippingItsSnapshot(FxRobot robot) {
        var items = new ArrayList<>(List.of(1, 2));
        var started = new ArrayList<Integer>();
        robot.interact(() -> {
            var result = FXTaskGroup.startAll(items, item -> {
                started.add(item);
                items.clear();
                return FXTask.empty();
            });
            assertThat(result.task().isDone()).isTrue();
        });
        assertThat(started).containsExactly(1, 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void errorsAndNullTasksAreCollectedWithoutSkippingLaterStarters(boolean error, FxRobot robot) {
        var failure = new AssertionError("starter failed");
        var started = new ArrayList<Integer>();
        robot.interact(() -> {
            var result = FXTaskGroup.startAll(List.of(1, 2), item -> {
                started.add(item);
                if (item == 1) {
                    if (error) throw failure;
                    return null;
                }
                return FXTask.empty();
            });
            if (error) {
                // JavaFX Task wraps Error in Exception for its Future; settlement retains the Error itself.
                assertThatThrownBy(() -> result.task().get()).hasRootCause(failure);
                assertThatThrownBy(() -> result.settled().toCompletableFuture().get()).hasCause(failure);
            } else assertThatThrownBy(() -> result.task().get()).hasCauseInstanceOf(NullPointerException.class);
        });
        assertThat(started).containsExactly(1, 2);
    }

    @Test
    void startsEveryItemAndWaitsForAllBeforeReportingFailuresInInputOrder(FxRobot robot) throws Exception {
        var first = new IllegalStateException("cannot start first");
        var later = new IllegalArgumentException("second failed asynchronously");
        var failed = FXTask.task(() -> { throw later; });
        var last = FXTask.task(() -> null);
        var started = new ArrayList<Integer>();
        var result = new AtomicReference<FXTaskGroup.Batch>();
        robot.interact(() -> result.set(FXTaskGroup.startAll(List.of(1, 2, 3), item -> {
            started.add(item);
            if (item == 1) throw first;
            return item == 2 ? failed : last;
        })));
        assertThat(started).containsExactly(1, 2, 3);
        failed.run();
        assertThat(result.get().task().isDone()).isFalse();
        assertThat(result.get().settled().toCompletableFuture().isDone()).isFalse();
        last.run();
        assertThatThrownBy(() -> result.get().task().get(5, TimeUnit.SECONDS)).hasCause(first);
        assertThatThrownBy(() -> result.get().settled().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(first);
        assertThat(first.getSuppressed()).containsExactly(later);
    }

    @Test
    void cancelledChildStillWaitsForOtherChildrenAndCancelsOnlyAfterTheySettle(FxRobot robot) throws Exception {
        var cancelled = FXTask.task(() -> null);
        var last = FXTask.task(() -> null);
        var result = new AtomicReference<FXTaskGroup.Batch>();
        robot.interact(() -> {
            result.set(FXTaskGroup.startAll(List.of(cancelled, last), task -> task));
            cancelled.cancel();
        });
        assertThat(result.get().task().isDone()).isFalse();
        last.run();
        assertThatThrownBy(() -> result.get().task().get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
        result.get().settled().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void aRealFailureTakesPrecedenceOverChildCancellation(FxRobot robot) {
        var cancelled = FXTask.task(() -> null);
        var failure = new IllegalStateException("cleanup failed");
        var failed = FXTask.task(() -> { throw failure; });
        var result = new AtomicReference<FXTaskGroup.Batch>();
        robot.interact(() -> {
            result.set(FXTaskGroup.startAll(List.of(cancelled, failed), task -> task));
            cancelled.cancel();
        });
        failed.run();
        assertThatThrownBy(() -> result.get().task().get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThat(result.get().task().isCancelled()).isFalse();
    }

    @Test
    void cancellingTheAggregateCannotCancelChildrenOrHideALaterCleanupFailure(FxRobot robot) {
        var failure = new IllegalStateException("late cleanup failed");
        var child = FXTask.task(() -> { throw failure; });
        var result = new AtomicReference<FXTaskGroup.Batch>();
        robot.interact(() -> {
            result.set(FXTaskGroup.startAll(List.of(child), task -> task));
            result.get().task().cancel();
        });
        var observer = result.get().settled().toCompletableFuture();
        observer.cancel(false);
        assertThat(child.isCancelled()).isFalse();
        assertThat(result.get().settled().toCompletableFuture().isDone()).isFalse();
        child.run();
        assertThatThrownBy(() -> result.get().settled().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
        assertThatThrownBy(() -> result.get().task().get()).isInstanceOf(CancellationException.class);
    }

    @Test
    void emptyAndAlreadyCompletedGroupsFinishWithoutSubmittingAnotherTask(FxRobot robot) {
        robot.interact(() -> {
            var empty = FXTaskGroup.startAll(List.<Integer>of(), ignored -> { throw new AssertionError(); });
            assertThat(empty.task().isDone()).isTrue();
            var completed = FXTaskGroup.startAll(List.of(FXTask.empty(), FXTask.of("done")), task -> task);
            assertThat(completed.task().isDone()).isTrue();
            assertThat(completed.settled().toCompletableFuture().isDone()).isTrue();
        });
    }

    @Test
    void aggregationCompletesWithoutFxEventDeliveryOrAnotherExecutorSlot(FxRobot robot) {
        try (var executor = Executors.newSingleThreadExecutor()) {
            robot.interact(() -> {
                var children = List.of(FXTask.task(() -> "first"), FXTask.task(() -> "second"));
                var result = FXTaskGroup.startAll(children, task -> {
                    executor.execute(task);
                    return task;
                });
                result.settled().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
                assertThatCode(() -> result.task().get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
            });
        }
    }

    @Test
    void repeatedFailureInstancesAreNotAddedAsTheirOwnSuppressedExceptions(FxRobot robot) {
        var failure = new IllegalStateException("shared failure");
        robot.interact(() -> {
            var result = FXTaskGroup.startAll(List.of(1, 2), item -> { throw failure; });
            assertThatThrownBy(() -> result.task().get()).hasCause(failure);
            assertThat(failure.getSuppressed()).isEmpty();
        });
    }
}
