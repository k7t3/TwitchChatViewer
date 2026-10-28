package com.github.k7t3.tcv.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationShutdownTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectedExecutionCompletesTheRequestExceptionally(boolean workerRejected) {
        var workerQueue = new ArrayList<Runnable>();
        var uiQueue = new ArrayList<Runnable>();
        var events = new ArrayList<String>();
        var rejection = new RejectedExecutionException("executor unavailable");
        Executor rejecting = command -> { throw rejection; };
        Executor worker = workerRejected ? rejecting : workerQueue::add;
        Executor ui = workerRejected ? uiQueue::add : rejecting;
        var shutdown = new ApplicationShutdown(worker, ui,
                () -> events.add("prepare"), () -> events.add("release"), () -> events.add("exit"));

        var completion = shutdown.request();
        if (workerRejected) uiQueue.removeFirst().run();
        else workerQueue.removeFirst().run();

        assertThatThrownBy(() -> completion.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class).hasCause(rejection);
        assertThat(events).containsExactlyElementsOf(workerRejected
                ? List.of("prepare", "release", "exit") : List.of("prepare", "release"));
    }

    @Test
    void failuresStillAttemptReleaseAndExitAndRemainObservable() {
        var workerQueue = new ArrayList<Runnable>();
        var uiQueue = new ArrayList<Runnable>();
        var events = new ArrayList<String>();
        var first = new IllegalStateException("prepare failed");
        var second = new IllegalArgumentException("release failed");
        var third = new IllegalStateException("exit failed");
        var shutdown = new ApplicationShutdown(workerQueue::add, uiQueue::add,
                () -> { events.add("prepare"); throw first; },
                () -> { events.add("release"); throw second; },
                () -> { events.add("exit"); throw third; });

        var completion = shutdown.request();
        workerQueue.removeFirst().run();
        uiQueue.removeFirst().run();

        assertThat(events).containsExactly("prepare", "release", "exit");
        assertThatThrownBy(() -> completion.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class).hasCause(first);
        assertThat(first.getSuppressed()).containsExactly(second, third);
    }

    @Test
    void preparesImmediatelyAndReleasesResourcesBeforeExit() {
        var workerQueue = new ArrayList<Runnable>();
        var uiQueue = new ArrayList<Runnable>();
        var events = new ArrayList<String>();
        var shutdown = new ApplicationShutdown(workerQueue::add, uiQueue::add,
                () -> events.add("prepare"), () -> events.add("release"), () -> events.add("exit"));

        var completion = shutdown.request();

        assertThat(events).containsExactly("prepare");
        assertThat(workerQueue).hasSize(1);
        assertThat(completion.toCompletableFuture().isDone()).isFalse();
        workerQueue.removeFirst().run();
        assertThat(events).containsExactly("prepare", "release");
        assertThat(shutdown.resourcesReleased().toCompletableFuture().isDone()).isTrue();
        assertThat(completion.toCompletableFuture().isDone()).isFalse();
        assertThat(uiQueue).hasSize(1);
        uiQueue.removeFirst().run();
        assertThat(events).containsExactly("prepare", "release", "exit");
        assertThat(completion.toCompletableFuture().isDone()).isTrue();
        assertThat(completion.toCompletableFuture().isCompletedExceptionally()).isFalse();
    }
}
