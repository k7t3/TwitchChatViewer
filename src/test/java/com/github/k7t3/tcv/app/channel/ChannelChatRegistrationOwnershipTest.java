package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelChatRegistrationOwnershipTest {
    @ParameterizedTest(name = "late cleanup after registration Error={0}")
    @ValueSource(booleans = {false, true})
    void rejectedRegistrationAndExecutorStillWaitForJoinAndPreserveAllCleanupFailures(boolean fatal, FxRobot robot) throws Exception {
        var channel = mock(TwitchChannel.class);
        var room = mock(ChatRoom.class);
        var joinEntered = new CountDownLatch(1);
        var joinRelease = new CountDownLatch(1);
        var leaveEntered = new CountDownLatch(1);
        var leaveRelease = new CountDownLatch(1);
        Throwable registration = fatal ? new LinkageError("registration unavailable") : new IllegalStateException("registration unavailable");
        var leaveFailure = new IllegalArgumentException("SDK leave failed");
        var repositoryFailure = new IllegalStateException("repository release failed");
        var releases = new AtomicInteger();
        var rejecting = new AtomicBoolean();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        when(channel.getOrJoinChatRoom()).thenAnswer(call -> {
            joinEntered.countDown();
            assertThat(joinRelease.await(5, TimeUnit.SECONDS)).isTrue();
            return room;
        });
        doAnswer(call -> {
            leaveEntered.countDown();
            assertThat(leaveRelease.await(5, TimeUnit.SECONDS)).isTrue();
            throw leaveFailure;
        }).when(channel).leaveChat();
        try (var worker = Executors.newSingleThreadExecutor()) {
            Executor executor = task -> {
                if (rejecting.get()) throw new RejectedExecutionException("worker stopping");
                worker.execute(task);
            };
            var connection = new ChannelChatConnection(channel, () -> {
                releases.incrementAndGet();
                throw repositoryFailure;
            }, executor, stage -> {
                stages.add(stage);
                if (rejecting.get()) {
                    if (registration instanceof RuntimeException exception) throw exception;
                    throw (Error) registration;
                }
            });
            try {
                robot.interact(connection::join);
                assertThat(joinEntered.await(5, TimeUnit.SECONDS)).isTrue();
                rejecting.set(true);
                robot.interact(() -> {
                    assertThatThrownBy(connection::close).isSameAs(registration);
                    var release = stages.getLast().toCompletableFuture();
                    assertThat(release.isDone()).isFalse();
                    joinRelease.countDown();
                    try { assertThat(leaveEntered.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                    assertThat(release.isDone()).isFalse();
                    leaveRelease.countDown();
                    assertThatThrownBy(() -> release.get(5, TimeUnit.SECONDS)).hasCause(registration);
                });
                assertThat(registration.getSuppressed()).containsExactly(leaveFailure);
                assertThat(leaveFailure.getSuppressed()).containsExactly(repositoryFailure);
                assertThat(releases).hasValue(1);
                verify(channel).leaveChat();
            } finally {
                joinRelease.countDown();
                leaveRelease.countDown();
                robot.interact(() -> {
                    try { connection.close(); }
                    catch (RuntimeException | Error error) { assertThat(error).isSameAs(registration); }
                });
            }
        }
    }

    @Test
    void rejectedCleanupRegistrationCannotSkipTheAcceptedConnectionsRelease(FxRobot robot) throws Exception {
        var channel = mock(TwitchChannel.class);
        var room = mock(ChatRoom.class);
        when(channel.getOrJoinChatRoom()).thenReturn(room);
        var queue = new LinkedBlockingQueue<Runnable>();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        var reject = new AtomicBoolean();
        var failure = new IllegalStateException("shutdown registration rejected");
        var releases = new AtomicInteger();
        var connection = new ChannelChatConnection(channel, releases::incrementAndGet, queue::add, stage -> {
            stages.add(stage);
            if (reject.get()) throw failure;
        });
        var joining = new AtomicReference<FXTask<ChatRoom>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> joining.set(connection.join()));
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                assertThat(joining.get().get(5, TimeUnit.SECONDS)).isSameAs(room);
                reject.set(true);

                robot.interact(() -> assertThatThrownBy(connection::close).isSameAs(failure));

                assertThat(stages.getLast().toCompletableFuture().isDone()).isFalse();
                assertThat(queue).hasSize(1);
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                assertThatThrownBy(() -> stages.getLast().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
                verify(channel).leaveChat();
                assertThat(releases).hasValue(1);
            } finally {
                reject.set(false);
                robot.interact(connection::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void aRejectedJoinRegistrationSettlesTheAttemptAndAllowsRetry(FxRobot robot) throws Exception {
        var channel = mock(TwitchChannel.class);
        var room = mock(ChatRoom.class);
        when(channel.getOrJoinChatRoom()).thenReturn(room);
        var queue = new LinkedBlockingQueue<Runnable>();
        var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
        var reject = new AtomicBoolean(true);
        var failure = new IllegalStateException("shutdown registration rejected");
        var releases = new AtomicInteger();
        var connection = new ChannelChatConnection(channel, releases::incrementAndGet, queue::add, stage -> {
            stages.add(stage);
            if (reject.getAndSet(false)) throw failure;
        });
        var joining = new AtomicReference<FXTask<ChatRoom>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> assertThatThrownBy(connection::join).isSameAs(failure));

                assertThat(connection.retirement().toCompletableFuture().isDone()).isTrue();
                assertThatCode(() -> stages.getFirst().toCompletableFuture().join()).doesNotThrowAnyException();
                assertThat(queue).isEmpty();
                verify(channel, never()).getOrJoinChatRoom();
                robot.interact(() -> joining.set(connection.join()));
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);

                assertThat(joining.get().get(5, TimeUnit.SECONDS)).isSameAs(room);
                verify(channel).loadBadgesIfNotLoaded();
                verify(channel).getOrJoinChatRoom();
                assertThat(releases).hasValue(0);
            } finally {
                robot.interact(connection::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
            }
        }
    }
}
