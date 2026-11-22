package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatResourceReleaseOwnershipTest {
    @Test
    void aLoadCancelledBeforeItStartsDoesNotRequireTheQueuedWrapperToRun(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var helper = new AppHelper(mock(TaskWorker.class), mock(EventPublishers.class), unexpected -> { throw new AssertionError(unexpected); });
        var model = new ChatRoomContainerViewModel(
                () -> new ChatRoomContainerViewModel.LoadRequest(mock(ChatFilters.class), () -> {
                    throw new AssertionError("cancelled provider must not run");
                }), queue::add, helper::awaitOnShutdown);
        try (var waiters = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.loadAsync();
                model.close();
            });
            var closing = waiters.submit(helper::close);
            try {
                assertThatCode(() -> closing.get(1, TimeUnit.SECONDS)).doesNotThrowAnyException();
                assertThat(queue).hasSize(1);
            } finally {
                drainQueuedCleanup(queue);
                closing.get(5, TimeUnit.SECONDS);
            }
        } finally {
            robot.interact(model::close);
            drainQueuedCleanup(queue);
            helper.close();
        }
    }

    @ParameterizedTest(name = "partial load cleanup failure, cancelled={0}")
    @ValueSource(booleans = {false, true})
    void failedPartialLoadCleanupRemainsObservableEvenIfTheTaskWasCancelled(boolean cancelled, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var helper = new AppHelper(mock(TaskWorker.class), mock(EventPublishers.class), unexpected -> { throw new AssertionError(unexpected); });
        var emoji = mock(Emoji.class);
        when(emoji.validateArchive()).thenReturn(true);
        var loadFailure = new IllegalStateException("badges could not be loaded");
        var cleanupFailure = new IllegalStateException("partial archive could not be closed");
        doThrow(cleanupFailure).when(emoji).close();
        var loader = new ChatResourcesLoader(() -> emoji, () -> {
            entered.countDown();
            awaitIgnoringInterruption(release);
            throw loadFailure;
        }, () -> new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(
                () -> new ChatRoomContainerViewModel.LoadRequest(mock(ChatFilters.class), loader::load),
                queue::add, helper::awaitOnShutdown);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(model::loadAsync);
                var execution = worker.submit(queue.remove());
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                if (cancelled) robot.interact(model::close);
                release.countDown();
                execution.get(5, TimeUnit.SECONDS);
                robot.interact(model::close);

                assertThatThrownBy(helper::close).hasCause(loadFailure).satisfies(failure ->
                        assertThat(failure.getCause().getSuppressed()).containsExactly(cleanupFailure));

                verify(emoji).close();
            } finally { release.countDown(); }
        } finally {
            robot.interact(model::close);
            drainQueuedCleanup(queue);
            try { helper.close(); }
            catch (RuntimeException error) {
                if (error.getCause() != loadFailure) throw error;
            }
        }
    }

    @Test
    void applicationShutdownWaitsForTheCancelledLoaderAndArchiveRelease(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var workerStopped = new CountDownLatch(1);
        var order = new CopyOnWriteArrayList<String>();
        var ownedWorker = mock(TaskWorker.class);
        doAnswer(call -> { workerStopped.countDown(); return null; }).when(ownedWorker).close();
        var publishers = mock(EventPublishers.class);
        doAnswer(call -> { order.add("publisher"); return null; }).when(publishers).close();
        var helper = new AppHelper(ownedWorker, publishers, unexpected -> { throw new AssertionError(unexpected); });
        var emoji = mock(Emoji.class);
        doAnswer(call -> { order.add("archive"); return null; }).when(emoji).close();
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(
                () -> new ChatRoomContainerViewModel.LoadRequest(mock(ChatFilters.class), () -> {
                    entered.countDown();
                    awaitIgnoringInterruption(release);
                    return resources;
                }), queue::add, helper::awaitOnShutdown);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(model::loadAsync);
                worker.submit(queue.remove());
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                var closing = worker.submit(helper::close);
                assertThat(workerStopped.await(5, TimeUnit.SECONDS)).isTrue();

                assertThat(closing.isDone()).isFalse();
                verify(publishers, never()).close();
                release.countDown();
                closing.get(5, TimeUnit.SECONDS);

                assertThat(order).containsExactly("archive", "publisher");
                verify(emoji).close();
            } finally { release.countDown(); }
        } finally {
            robot.interact(model::close);
            drainQueuedCleanup(queue);
            helper.close();
            resources.close();
        }
    }

    @ParameterizedTest(name = "ordinary load failure, rejected={0}")
    @ValueSource(booleans = {false, true})
    void anOrdinaryLoadFailureRemainsAUiErrorInsteadOfFailingApplicationShutdown(boolean rejected, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var helper = new AppHelper(mock(TaskWorker.class), mock(EventPublishers.class), unexpected -> { throw new AssertionError(unexpected); });
        var model = new ChatRoomContainerViewModel(
                () -> new ChatRoomContainerViewModel.LoadRequest(mock(ChatFilters.class), () -> {
                    throw new java.io.IOException("badges could not be loaded");
                }), command -> {
                    if (rejected) throw new RejectedExecutionException("worker is unavailable");
                    queue.add(command);
                }, helper::awaitOnShutdown);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(model::loadAsync);
            if (!rejected) worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(model.isLoaded()).isFalse();
                assertThat(model.isLoading()).isFalse();
                assertThat(model.getError()).isNotBlank();
                model.close();
            });

            assertThatCode(helper::close).doesNotThrowAnyException();
        } finally {
            robot.interact(model::close);
            drainQueuedCleanup(queue);
            helper.close();
        }
    }

    @ParameterizedTest(name = "late resource close failure, close={0}")
    @ValueSource(booleans = {false, true})
    void releasingALateCancelledLoadKeepsItsFailureForApplicationShutdown(boolean close, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var helper = new AppHelper(mock(TaskWorker.class), mock(EventPublishers.class), unexpected -> { throw new AssertionError(unexpected); });
        var emoji = mock(Emoji.class);
        var failure = new IllegalStateException("late archive could not be closed");
        doThrow(failure).when(emoji).close();
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(
                () -> new ChatRoomContainerViewModel.LoadRequest(mock(ChatFilters.class), () -> {
                    entered.countDown();
                    awaitIgnoringInterruption(release);
                    return resources;
                }), queue::add, helper::awaitOnShutdown);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(model::loadAsync);
                var execution = worker.submit(queue.remove());
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> { if (close) model.close(); else model.onLogout(); });
                release.countDown();
                execution.get(5, TimeUnit.SECONDS);
                robot.interact(() -> assertThat(model.isLoaded()).isFalse());

                assertThatThrownBy(helper::close).isSameAs(failure);

                verify(emoji).close();
            } finally { release.countDown(); }
        } finally {
            robot.interact(model::close);
            drainQueuedCleanup(queue);
            try { helper.close(); }
            catch (RuntimeException error) { if (error != failure) throw error; }
            resources.close();
        }
    }

    @Test
    void anArchiveReleaseFailureRemainsObservableAtApplicationShutdown(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(mock(TaskWorker.class), publishers, unexpected -> { throw new AssertionError(unexpected); });
        var emoji = mock(Emoji.class);
        var failure = new IllegalStateException("chat archive could not be closed");
        doThrow(failure).when(emoji).close();
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(
                () -> new ChatRoomContainerViewModel.LoadRequest(mock(ChatFilters.class), () -> resources),
                queue::add, helper::awaitOnShutdown);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(model::loadAsync);
            worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(model.isLoaded()).isTrue();
                model.close();
            });
            var cleanup = queue.remove();
            worker.submit(() -> {
                try { cleanup.run(); }
                catch (RuntimeException | Error uncaught) {
                    // 終了所有者への通知を検証するため、旧実装の未捕捉失敗もここで回収する。
                    assertThat(uncaught).isSameAs(failure);
                }
            }).get(5, TimeUnit.SECONDS);

            assertThatThrownBy(helper::close).isSameAs(failure);

            verify(emoji).close();
            verify(publishers).close();
        } finally {
            robot.interact(model::close);
            drainQueuedCleanup(queue);
            try { helper.close(); }
            catch (RuntimeException error) { if (error != failure) throw error; }
            resources.close();
        }
    }

    private static void awaitIgnoringInterruption(CountDownLatch release) {
        var interrupted = false;
        while (true) {
            try { release.await(); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static void drainQueuedCleanup(LinkedBlockingQueue<Runnable> queue) {
        for (var action = queue.poll(); action != null; action = queue.poll()) {
            try { action.run(); }
            catch (RuntimeException | Error ignored) {
                // assertion途中の失敗でも終了待ちを残さない。解放失敗の検証は本体で行う。
            }
        }
    }
}
