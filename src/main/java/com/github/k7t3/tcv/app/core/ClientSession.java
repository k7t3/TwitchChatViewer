package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.lifecycle.CloseActions;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** 一回のログインで作成したドメインと SDK クライアントを、終了完了まで所有する。 */
final class ClientSession implements AutoCloseable {
    private final Twitch twitch;
    private final ChannelRepository channels;
    private final Object lifecycleLock = new Object();
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private final CompletionStage<Void> result = completed.minimalCompletionStage();
    private boolean logoutRequested;
    private boolean started;
    private Thread closingThread;
    private CompletionStage<Void> beforeEnd = CompletableFuture.completedFuture(null);

    ClientSession(Twitch twitch, ChannelRepository channels) {
        this.twitch = Objects.requireNonNull(twitch);
        this.channels = Objects.requireNonNull(channels);
    }

    Twitch twitch() { return twitch; }
    ChannelRepository channels() { return channels; }
    CompletionStage<Void> completion() { return result; }

    void requestLogout() {
        requestLogout(CompletableFuture.completedFuture(null));
    }

    void requestLogout(CompletionStage<Void> detached) {
        synchronized (lifecycleLock) {
            if (started) throw new IllegalStateException("session is already ending");
            logoutRequested = true;
            beforeEnd = Objects.requireNonNull(detached);
            twitch.freezeCredentials();
        }
    }

    void requestClose(CompletionStage<Void> detached) {
        synchronized (lifecycleLock) {
            if (started) throw new IllegalStateException("session is already ending");
            beforeEnd = Objects.requireNonNull(detached);
            twitch.freezeCredentials();
        }
    }

    @Override
    public void close() {
        final boolean owner;
        final boolean logout;
        final CompletionStage<Void> prerequisite;
        synchronized (lifecycleLock) {
            if (closingThread == Thread.currentThread()) return;
            owner = !started;
            if (owner) {
                started = true;
                closingThread = Thread.currentThread();
            }
            logout = logoutRequested;
            prerequisite = beforeEnd;
        }
        if (!owner) {
            try { completed.join(); }
            catch (CompletionException failure) { rethrow(failure.getCause()); }
            return;
        }
        try {
            CloseActions.runAll(twitch::freezeCredentials, () -> {
                try { prerequisite.toCompletableFuture().join(); }
                catch (CompletionException failure) { rethrow(failure.getCause()); }
            }, channels::clear, logout ? twitch::logout : twitch::close);
            completed.complete(null);
        } catch (RuntimeException | Error failure) {
            completed.completeExceptionally(failure);
            throw failure;
        } finally {
            synchronized (lifecycleLock) { closingThread = null; }
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException(failure);
    }
}
