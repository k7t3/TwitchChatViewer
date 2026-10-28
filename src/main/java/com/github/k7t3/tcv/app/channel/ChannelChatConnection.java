package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 一つの表示モデルの参加・退出を直列化し、観測の取消や終了登録の失敗でも受付済みの退出を止めない。 */
final class ChannelChatConnection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelChatConnection.class);
    private final Object lock = new Object();
    private final TwitchChannel channel;
    private final Runnable releaseChannel;
    private final Executor executor;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private CompletionStage<Void> tail = CompletableFuture.completedStage(null);
    private Operation<ChatRoom> joining;
    private Operation<Void> leaving;
    private boolean desiredJoined;
    private boolean closed;
    private boolean retiring;
    private long generation;

    ChannelChatConnection(TwitchChannel channel, Runnable releaseChannel, Executor executor,
                          Consumer<CompletionStage<Void>> shutdownAwaiter) {
        this.channel = channel;
        this.releaseChannel = releaseChannel;
        this.executor = executor;
        this.shutdownAwaiter = shutdownAwaiter;
    }

    FXTask<ChatRoom> join() {
        final Operation<ChatRoom> operation;
        final CompletionStage<Void> previous;
        synchronized (lock) {
            if (isRetiringLocked()) throw new IllegalStateException("channel chat connection is closed");
            if (desiredJoined && joining != null && !joining.result.isCancelled() && joining.failure == null) {
                return joining.result;
            }
            desiredJoined = true;
            long token = ++generation;
            operation = new Operation<>(false, () -> {
                checkCurrentJoin(token);
                channel.loadBadgesIfNotLoaded();
                checkCurrentJoin(token);
                return channel.getOrJoinChatRoom();
            });
            joining = operation;
            previous = tail;
            tail = operation.drained.minimalCompletionStage();
            operation.result.completion().whenComplete((ignored, failure) -> {
                if (operation.result.isCancelled()) cancelJoin(operation);
            });
        }
        startAfter(previous, operation);
        return operation.result;
    }

    private void checkCurrentJoin(long token) {
        synchronized (lock) {
            if (closed || !desiredJoined || generation != token) throw new CancellationException("join was superseded");
        }
    }

    private void cancelJoin(Operation<ChatRoom> operation) {
        leave(operation);
    }

    FXTask<Void> leave() {
        return leave(null, false);
    }

    private FXTask<Void> leave(Operation<ChatRoom> expectedJoin) {
        return leave(expectedJoin, false);
    }

    private FXTask<Void> leave(Operation<ChatRoom> expectedJoin, boolean closing) {
        final Operation<Void> operation;
        final CompletionStage<Void> previous;
        synchronized (lock) {
            if (closing) {
                if (closed) return leaving == null ? FXTask.empty() : leaving.result;
                closed = true;
            }
            if (expectedJoin != null && (joining != expectedJoin || !desiredJoined || closed)) return FXTask.empty();
            if (!desiredJoined && leaving != null && (closed || leaving.failure == null)) return leaving.result;
            desiredJoined = false;
            generation++;
            if (joining == null) return FXTask.empty();
            if (!channel.isPersistent()) retiring = true;
            joining.result.cancel(false);
            operation = new Operation<>(true, () -> {
                CloseActions.runAll(channel::leaveChat, releaseChannel);
                return null;
            });
            leaving = operation;
            previous = tail;
            tail = operation.drained.minimalCompletionStage();
        }
        startAfter(previous, operation);
        return operation.result;
    }

    boolean isCurrentJoin(FXTask<ChatRoom> task) {
        synchronized (lock) {
            return !closed && desiredJoined && joining != null && joining.result == task && !task.isCancelled();
        }
    }

    boolean isCurrentLeave(FXTask<Void> task) {
        synchronized (lock) { return !closed && !desiredJoined && leaving != null && leaving.result == task; }
    }

    private boolean isRetiringLocked() {
        return retiring || closed || (!desiredJoined && leaving != null && !channel.isPersistent());
    }
    boolean isRetiring() { synchronized (lock) { return isRetiringLocked(); } }

    CompletionStage<Void> retirement() { synchronized (lock) { return tail; } }

    @Override
    public void close() {
        leave(null, true);
    }

    private void startAfter(CompletionStage<Void> previous, Operation<?> operation) {
        try { shutdownAwaiter.accept(operation.drained.minimalCompletionStage()); }
        catch (RuntimeException | Error rejected) {
            if (operation.cleanup) {
                // 参加済み資源の後始末は、追跡の登録に失敗しても実行する。
                operation.registrationFailure = rejected;
                executeAfter(previous, operation);
            } else operation.finish(null, rejected);
            throw rejected;
        }
        executeAfter(previous, operation);
    }

    private void executeAfter(CompletionStage<Void> previous, Operation<?> operation) {
        previous.whenComplete((ignored, failure) -> {
            try {
                executor.execute(operation::run);
            } catch (RuntimeException | Error rejected) {
                if (operation.cleanup) {
                    try { Thread.ofVirtual().name("TCV-Chat-Leave").start(operation::run); }
                    catch (RuntimeException | Error fallbackFailure) {
                        if (fallbackFailure != rejected) rejected.addSuppressed(fallbackFailure);
                        operation.finish(null, rejected);
                    }
                } else operation.finish(null, rejected);
            }
        });
    }

    private static final class Operation<T> {
        private final boolean cleanup;
        private final Callable<T> action;
        private final CompletableFuture<Void> drained = new CompletableFuture<>();
        private T value;
        private Throwable registrationFailure;
        private volatile Throwable failure;
        private final FXTask<T> result = FXTask.observation(() -> {
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new IllegalStateException(failure);
            return value;
        });

        Operation(boolean cleanup, Callable<T> action) {
            this.cleanup = cleanup;
            this.action = action;
        }

        void run() {
            T value = null;
            Throwable failure = null;
            try { value = action.call(); }
            catch (Throwable error) { failure = error; }
            finish(value, failure);
        }

        void finish(T value, Throwable failure) {
            if (registrationFailure != null) {
                if (failure != null && failure != registrationFailure) registrationFailure.addSuppressed(failure);
                failure = registrationFailure;
            }
            this.value = value;
            this.failure = failure;
            try {
                result.run();
            } finally {
                if (cleanup && failure != null && !(failure instanceof CancellationException)) {
                    LOGGER.error("Failed to release channel chat", failure);
                    drained.completeExceptionally(failure);
                } else drained.complete(null);
            }
        }
    }
}
