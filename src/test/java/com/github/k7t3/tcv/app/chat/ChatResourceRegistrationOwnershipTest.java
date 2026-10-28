package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(ApplicationExtension.class)
class ChatResourceRegistrationOwnershipTest {
    @ParameterizedTest(name = "fallback close failure, registration rejected={0}, Error={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void fallbackCloseFailureReachesItsActualStageEvenWhenTrackingWasRefused(
            boolean refuseRegistration, boolean fatalCleanup, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        var executions = new AtomicInteger();
        var registrations = new AtomicInteger();
        var registrationFailure = new IllegalStateException("release tracking rejected");
        Throwable cleanupFailure = fatalCleanup ? new AssertionError("archive close failed")
                : new IllegalStateException("archive close failed");
        var emoji = mock(Emoji.class);
        doAnswer(call -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            throw cleanupFailure;
        }).when(emoji).close();
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(() -> new ChatRoomContainerViewModel.LoadRequest(
                mock(ChatFilters.class), () -> resources), task -> {
            if (executions.incrementAndGet() == 1) queue.add(task);
            else throw new RejectedExecutionException("release worker unavailable");
        }, stage -> {
            stages.add(stage);
            if (registrations.incrementAndGet() == 2 && refuseRegistration) throw registrationFailure;
        });
        var load = new AtomicReference<FXTask<Void>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> load.set(model.loadAsync()));
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                load.get().get(5, TimeUnit.SECONDS);
                robot.interact(() -> {
                    if (refuseRegistration) assertThatThrownBy(model::close).isSameAs(registrationFailure);
                    else assertThatCode(model::close).doesNotThrowAnyException();
                    assertThat(model.isClosed()).isTrue();
                    assertThat(model.isLoaded()).isFalse();
                });

                assertThatThrownBy(() -> stages.getLast().toCompletableFuture().get(5, TimeUnit.SECONDS))
                        .hasCause(cleanupFailure);

                stages.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(executions).hasValue(2);
                verify(emoji).close();
            } finally {
                robot.interact(model::close);
                resources.close();
            }
        }
    }

    @ParameterizedTest(name = "release worker refusal={0}")
    @ValueSource(strings = {"rejected", "runtime", "error"})
    void aRefusedReleaseRunsOutsideFxAndRemainsOwnedUntilTheArchiveActuallyCloses(String startup, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        var executions = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        Throwable refusal = switch (startup) {
            case "runtime" -> new IllegalStateException("worker cannot start");
            case "error" -> new AssertionError("worker cannot start");
            default -> new RejectedExecutionException("worker has stopped accepting releases");
        };
        var emoji = mock(Emoji.class);
        doAnswer(call -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            entered.countDown();
            if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("archive release was not allowed to finish");
            return null;
        }).when(emoji).close();
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(() -> new ChatRoomContainerViewModel.LoadRequest(
                mock(ChatFilters.class), () -> resources), task -> {
            if (executions.incrementAndGet() == 1) queue.add(task);
            else {
                if (refusal instanceof RuntimeException failure) throw failure;
                throw (Error) refusal;
            }
        }, stages::add);
        var load = new AtomicReference<FXTask<Void>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> load.set(model.loadAsync()));
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                load.get().get(5, TimeUnit.SECONDS);

                robot.interact(() -> assertThatCode(model::close).doesNotThrowAnyException());

                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(stages.getLast().toCompletableFuture().isDone()).isFalse();
                robot.interact(() -> {
                    assertThat(model.isClosed()).isTrue();
                    assertThat(model.isLoaded()).isFalse();
                    assertThat(model.isLoading()).isFalse();
                });
                finish.countDown();
                for (var stage : stages) stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(executions).hasValue(2);
                verify(emoji).close();
            } finally {
                finish.countDown();
                robot.interact(model::close);
                resources.close();
            }
        }
    }

    @ParameterizedTest(name = "refused release registration Error={0}")
    @ValueSource(booleans = {false, true})
    void refusedReleaseRegistrationStillClosesTheAdoptedArchiveAndSettlesItsActualStage(boolean fatal, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        var registrations = new AtomicInteger();
        Throwable refusal = fatal ? new AssertionError("release tracking rejected") : new IllegalStateException("release tracking rejected");
        var emoji = mock(Emoji.class);
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(() -> new ChatRoomContainerViewModel.LoadRequest(
                mock(ChatFilters.class), () -> resources), queue::add, stage -> {
            stages.add(stage);
            if (registrations.incrementAndGet() == 2) {
                if (refusal instanceof RuntimeException failure) throw failure;
                throw (Error) refusal;
            }
        });
        var load = new AtomicReference<FXTask<Void>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> load.set(model.loadAsync()));
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                load.get().get(5, TimeUnit.SECONDS);
                robot.interact(() -> {
                    assertThat(model.isLoaded()).isTrue();

                    assertThatThrownBy(model::close).isSameAs(refusal);

                    assertThat(model.isClosed()).isTrue();
                    assertThat(model.isLoaded()).isFalse();
                    assertThat(model.isLoading()).isFalse();
                    assertThat(queue).hasSize(1);
                    verify(emoji, never()).close();
                });
                assertThat(stages.getLast().toCompletableFuture().isDone()).isFalse();
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                for (var stage : stages) stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
                robot.interact(model::close);
                assertThat(queue).isEmpty();
                verify(emoji).close();
            } finally {
                robot.interact(model::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
                resources.close();
            }
        }
    }

    @ParameterizedTest(name = "refused load registration Error={0}")
    @ValueSource(booleans = {false, true})
    void refusedLoadRegistrationEndsWaitingWithoutCallingTheProviderAndAllowsRetry(boolean fatal, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        var reject = new AtomicBoolean(true);
        var calls = new AtomicInteger();
        Throwable refusal = fatal ? new AssertionError("tracking rejected") : new IllegalStateException("tracking rejected");
        var filters = mock(ChatFilters.class);
        var snapshot = new ChatFilters.Snapshot(List.of(), List.of());
        var emoji = mock(Emoji.class);
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class), snapshot, mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(() -> new ChatRoomContainerViewModel.LoadRequest(filters, () -> {
            calls.incrementAndGet();
            return resources;
        }), queue::add, stage -> {
            stages.add(stage);
            if (reject.getAndSet(false)) {
                if (refusal instanceof RuntimeException failure) throw failure;
                throw (Error) refusal;
            }
        });
        var first = new AtomicReference<FXTask<Void>>();
        var retry = new AtomicReference<FXTask<Void>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> {
                    assertThatCode(() -> first.set(model.loadAsync())).doesNotThrowAnyException();
                    assertThat(first.get().isCancelled()).isTrue();
                    assertThat(model.isLoading()).isFalse();
                    assertThat(model.isLoaded()).isFalse();
                    assertThat(model.getError()).isNotBlank();
                    assertThat(queue).isEmpty();
                    assertThat(calls).hasValue(0);
                    retry.set(model.loadAsync());
                    assertThat(model.isLoading()).isTrue();
                    assertThat(model.getError()).isNull();
                });
                stages.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS);
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                retry.get().get(5, TimeUnit.SECONDS);
                robot.interact(() -> {
                    assertThat(model.isLoaded()).isTrue();
                    assertThat(model.isLoading()).isFalse();
                    assertThat(model.getError()).isNull();
                    assertThat(calls).hasValue(1);
                    verify(filters).applySnapshot(snapshot);
                    model.close();
                });
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                for (var stage : stages) stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
                verify(emoji).close();
            } finally {
                robot.interact(model::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
                resources.close();
            }
        }
    }
}
