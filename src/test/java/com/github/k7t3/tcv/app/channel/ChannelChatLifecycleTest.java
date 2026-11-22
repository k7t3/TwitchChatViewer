package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.TaskWorker;
import org.junit.jupiter.api.AutoClose;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelChatLifecycleTest {
    @AutoClose
    private final TaskWorker worker = new TaskWorker();

    @Test
    void losingPersistentRetentionDuringLeavePreventsRejoiningTheRetiringModel(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            when(fixture.channel.isPersistent()).thenReturn(true);
            fixture.join();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(invocation -> { entered.countDown(); await(release); return null; }).when(fixture.channel).leaveChat();
            try {
                robot.interact(fixture.model::leaveChatAsync);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                when(fixture.channel.isPersistent()).thenReturn(false);
                robot.interact(() -> {
                    assertThat(fixture.model.isRetiring()).isTrue();
                    assertThatThrownBy(fixture.model::joinChatAsync).isInstanceOf(IllegalStateException.class);
                });
                release.countDown(); fixture.awaitDrained();
                verify(fixture.channel).getOrJoinChatRoom();
            } finally { release.countDown(); }
        }
    }

    @Test
    void cancellingAQueuedLeaveStillLeavesAfterTheBlockedJoinReturns(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            when(fixture.channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
                entered.countDown(); await(release); return fixture.room;
            });
            try {
                robot.interact(fixture.model::joinChatAsync);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> fixture.model.leaveChatAsync().cancel(true));
                release.countDown();
                fixture.awaitDrained();
                verify(fixture.channel).leaveChat();
                verify(fixture.repository).releaseChannel(fixture.model);
            } finally { release.countDown(); }
        }
    }

    @Test
    void aJoinFailureAfterCloseCannotPublishAnErrorOrSkipTheQueuedLeave(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            when(fixture.channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
                entered.countDown(); await(release); throw new IllegalStateException("late SDK error");
            });
            try {
                robot.interact(fixture.model::joinChatAsync);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(fixture.model::close);
                release.countDown();
                fixture.awaitDrained();
                robot.interact(() -> {
                    assertThat(fixture.errors).isEmpty();
                    assertThat(fixture.model.isChatJoined()).isFalse();
                });
                verify(fixture.channel).leaveChat();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void executorStartupFailureFinishesTheRequestInsteadOfLeavingItPending(boolean fatal, FxRobot robot) {
        Throwable failure = fatal ? new AssertionError("thread creation failed") : new IllegalStateException("executor failed");
        try (var fixture = new Fixture(robot, task -> {
            if (failure instanceof Error error) throw error;
            throw (RuntimeException) failure;
        })) {
            var joining = new AtomicReference<FXTask<ChatRoom>>();
            robot.interact(() -> joining.set(fixture.model.joinChatAsync()));
            if (fatal) assertThatThrownBy(() -> joining.get().get(5, TimeUnit.SECONDS)).hasRootCause(failure);
            else assertThatThrownBy(() -> joining.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            verify(fixture.channel, never()).getOrJoinChatRoom();
        }
    }

    @Test
    void repeatedJoinsShareOneRequestAndFailureAndCanRetry(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var failure = new IllegalStateException("join failed");
            var failedOnce = new AtomicBoolean();
            when(fixture.channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
                if (failedOnce.compareAndSet(false, true)) {
                    entered.countDown(); await(release); throw failure;
                }
                return fixture.room;
            });
            try {
                var first = new AtomicReference<FXTask<ChatRoom>>();
                robot.interact(() -> {
                    first.set(fixture.model.joinChatAsync());
                    assertThat(fixture.model.joinChatAsync()).isSameAs(first.get());
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                assertThatThrownBy(() -> first.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
                robot.interact(() -> assertThat(fixture.errors).containsExactly(failure));
                verify(fixture.channel).getOrJoinChatRoom();
                fixture.join();
                verify(fixture.channel, times(2)).getOrJoinChatRoom();
            } finally { release.countDown(); }
        }
    }

    @Test
    void closingDuringBadgeLoadingPreventsJoiningAndReleasesItsOwnedRequest(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(invocation -> { entered.countDown(); await(release); return null; })
                    .when(fixture.channel).loadBadgesIfNotLoaded();
            try {
                robot.interact(fixture.model::joinChatAsync);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    fixture.model.close();
                    assertThatThrownBy(fixture.model::joinChatAsync).isInstanceOf(IllegalStateException.class);
                    assertThat(fixture.drains.getFirst().toCompletableFuture().isDone()).isFalse();
                });
                release.countDown();
                fixture.awaitDrained();
                robot.interact(() -> assertThat(fixture.model.isChatJoined()).isFalse());
                verify(fixture.channel, never()).getOrJoinChatRoom();
                verify(fixture.repository).releaseChannel(fixture.model);
                verify(fixture.images, never()).close();
            } finally { release.countDown(); }
        }
    }

    @Test
    void cancellingJoinDoesNotReportPhysicalCompletionUntilItsCleanupFinishes(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            when(fixture.channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
                entered.countDown(); await(release); return fixture.room;
            });
            var joining = new AtomicReference<FXTask<ChatRoom>>();
            try {
                robot.interact(() -> joining.set(fixture.model.joinChatAsync()));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                joining.get().cancel(true);
                assertThatThrownBy(joining.get()::get).isInstanceOf(CancellationException.class);
                assertThat(fixture.drains.getFirst().toCompletableFuture().isDone()).isFalse();
                release.countDown();
                fixture.awaitDrained();
                verify(fixture.channel).leaveChat();
                robot.interact(() -> assertThat(fixture.model.isChatJoined()).isFalse());
            } finally { release.countDown(); }
        }
    }

    @Test
    void cancellingLeaveCannotSkipCleanupOrHideItsFailures(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            fixture.join();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var failure = new IllegalStateException("SDK leave failed");
            var second = new IllegalArgumentException("repository release failed");
            doAnswer(invocation -> { entered.countDown(); await(release); throw failure; })
                    .when(fixture.channel).leaveChat();
            doThrow(second).when(fixture.repository).releaseChannel(fixture.model);
            var leaving = new AtomicReference<FXTask<Void>>();
            try {
                robot.interact(() -> leaving.set(fixture.model.leaveChatAsync()));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> leaving.get().cancel(true));
                var cleanup = fixture.drains.getLast();
                assertThat(cleanup.toCompletableFuture().isDone()).isFalse();
                release.countDown();
                assertThatThrownBy(() -> cleanup.toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
                assertThat(failure.getSuppressed()).containsExactly(second);
                verify(fixture.repository).releaseChannel(fixture.model);
            } finally { release.countDown(); }
        }
    }

    @Test
    void persistentChannelCanRejoinAfterLeaveWithoutPublishingTheOlderJoin(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            when(fixture.channel.isPersistent()).thenReturn(true);
            var firstEntered = new CountDownLatch(1);
            var secondEntered = new CountDownLatch(1);
            var firstRelease = new CountDownLatch(1);
            var secondRelease = new CountDownLatch(1);
            var order = new CopyOnWriteArrayList<String>();
            when(fixture.channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
                assertThat(Platform.isFxApplicationThread()).isFalse();
                order.add("join");
                if (order.size() == 1) { firstEntered.countDown(); await(firstRelease); }
                else { secondEntered.countDown(); await(secondRelease); }
                return fixture.room;
            });
            doAnswer(invocation -> { order.add("leave"); return null; }).when(fixture.channel).leaveChat();
            var last = new AtomicReference<FXTask<ChatRoom>>();
            try {
                robot.interact(fixture.model::joinChatAsync);
                assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    fixture.model.leaveChatAsync();
                    last.set(fixture.model.joinChatAsync());
                });
                firstRelease.countDown();
                assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> assertThat(fixture.model.isChatJoined()).isFalse());
                secondRelease.countDown();
                last.get().get(5, TimeUnit.SECONDS);
                robot.interact(() -> assertThat(fixture.model.isChatJoined()).isTrue());
                assertThat(order).containsExactly("join", "leave", "join");
            } finally { firstRelease.countDown(); secondRelease.countDown(); }
        }
    }

    @Test
    void aCompletedJoinReturnsTheCachedRoomWithoutCallingTheSdkOnFx(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            fixture.join();
            robot.interact(() -> {
                var repeated = fixture.model.joinChatAsync();
                assertThat(repeated.isDone()).isTrue();
            });
            verify(fixture.channel).getOrJoinChatRoom();
        }
    }

    @Test
    void rejectedCleanupUsesAnOwnedFallbackAndCanFinishWithoutFxEvents(FxRobot robot) throws Exception {
        var rejecting = new AtomicBoolean();
        try (var pool = Executors.newSingleThreadExecutor(); var fixture = new Fixture(robot, task -> {
            if (rejecting.get()) throw new RejectedExecutionException("worker stopped");
            pool.execute(task);
        })) {
            fixture.join();
            rejecting.set(true);
            robot.interact(() -> {
                fixture.model.close();
                fixture.drains.getLast().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            });
            verify(fixture.channel).leaveChat();
            verify(fixture.repository).releaseChannel(fixture.model);
        }
    }

    @Test
    void leavingDuringJoinWaitsForTheJoinAndCannotRestoreJoinedState(FxRobot robot) throws Exception {
        var channel = mock(TwitchChannel.class);
        when(channel.getBroadcaster()).thenReturn(new Broadcaster("123", "channel", "Channel", "", ""));
        var room = mock(ChatRoom.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release join");
            return room;
        });
        var model = new TwitchChannelViewModel(channel, mock(ChannelViewModelRepository.class), mock(ImageLoader.class), worker.getExecutor(), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var joining = new AtomicReference<FXTask<ChatRoom>>();
        var leaving = new AtomicReference<FXTask<Void>>();
        try {
            robot.interact(() -> joining.set(model.joinChatAsync()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> leaving.set(model.leaveChatAsync()));
            assertThat(leaving.get().isDone()).as("leave must wait for an in-flight join").isFalse();
            release.countDown();
            leaving.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(model.isChatJoined()).isFalse());
            verify(channel).leaveChat();
        } finally {
            release.countDown();
            try { joining.get().get(5, TimeUnit.SECONDS); } catch (java.util.concurrent.CancellationException expected) { }
            robot.interact(model::close);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release operation");
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private static final class Fixture implements AutoCloseable {
        final java.util.concurrent.ExecutorService pool = Executors.newSingleThreadExecutor();
        final TwitchChannel channel = mock(TwitchChannel.class);
        final ChatRoom room = mock(ChatRoom.class);
        final ChannelViewModelRepository repository = mock(ChannelViewModelRepository.class);
        final ImageLoader images = mock(ImageLoader.class);
        final CopyOnWriteArrayList<CompletionStage<Void>> drains = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Throwable> errors = new CopyOnWriteArrayList<>();
        final TwitchChannelViewModel model;
        final FxRobot robot;

        Fixture(FxRobot robot) { this(robot, null); }

        Fixture(FxRobot robot, Executor executor) {
            this.robot = robot;
            when(channel.getBroadcaster()).thenReturn(new Broadcaster("123", "channel", "Channel", "", ""));
            when(channel.getOrJoinChatRoom()).thenAnswer(invocation -> {
                assertThat(Platform.isFxApplicationThread()).isFalse();
                return room;
            });
            doAnswer(invocation -> { drains.add(invocation.getArgument(0)); return null; })
                    .when(repository).trackChatOperation(any());
            model = new TwitchChannelViewModel(channel, repository, images, executor == null ? pool : executor, errors::add);
        }

        void join() throws Exception {
            var joining = new AtomicReference<FXTask<ChatRoom>>();
            robot.interact(() -> joining.set(model.joinChatAsync()));
            joining.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(model.isChatJoined()).isTrue());
        }

        void awaitDrained() throws Exception {
            for (var drained : drains) drained.toCompletableFuture().get(5, TimeUnit.SECONDS);
        }

        @Override public void close() {
            robot.interact(model::close);
            pool.close();
            for (var stage : drains) {
                stage.handle((ignored, failure) -> null).toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
            }
        }
    }
}
