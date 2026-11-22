package com.github.k7t3.tcv.app.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class SerialIoQueueTest {
    @Test
    void cancellingAnUnrelatedReadCannotInterruptAnEarlierAcceptedWrite() throws Exception {
        var scheduled = new LinkedBlockingQueue<Runnable>();
        var queue = new SerialIoQueue(scheduled::add);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var readFinished = new CountDownLatch(1);
        var write = queue.submit(() -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("unreleased write");
            return "saved";
        });
        try (var reader = Executors.newSingleThreadExecutor()) {
            var reading = reader.submit(() -> {
                try { return queue.call(() -> "snapshot"); }
                finally { readFinished.countDown(); }
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(reading.cancel(true)).isTrue();
                release.countDown();
                assertThat(write.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("saved");
                assertThat(readFinished.await(5, TimeUnit.SECONDS)).isTrue();
            } finally { release.countDown(); }
        }
        scheduled.remove().run();
    }

    @Test
    void acceptedWritesRunInOrderEvenWithManyExecutorThreads() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var queue = new SerialIoQueue(executor);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var values = new CopyOnWriteArrayList<Integer>();
            try {
                var first = queue.submit(() -> {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("unreleased write");
                    values.add(1); return 1;
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var second = queue.submit(() -> { values.add(2); return 2; });
                assertThat(second.toCompletableFuture().isDone()).isFalse();
                release.countDown();
                assertThat(first.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
                assertThat(second.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(2);
                assertThat(values).containsExactly(1, 2);
            } finally { release.countDown(); }
        }
    }

    @Test
    void backgroundReadCanDrainEarlierWritesWhenTheirPumpIsStillQueued() {
        var scheduled = new LinkedBlockingQueue<Runnable>();
        var queue = new SerialIoQueue(scheduled::add);
        var values = new ArrayList<Integer>();
        queue.submit(() -> { values.add(1); return null; });
        assertThat(queue.call(() -> java.util.List.copyOf(values))).containsExactly(1);
        scheduled.remove().run();
        assertThat(values).containsExactly(1);
    }

    @Test
    void aFailedWriteDoesNotLoseTheOriginalCauseOrPreventTheNextWrite() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var queue = new SerialIoQueue(executor);
            var failure = new IllegalStateException("write failed");
            var failed = queue.submit(() -> { throw failure; });
            var next = queue.submit(() -> "next write");
            assertThatThrownBy(() -> failed.toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(next.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo("next write");
        }
    }

    @Test
    void rejectedExecutionFinishesTheRequestWithoutPerformingIo() {
        var failure = new RejectedExecutionException("executor closed");
        var queue = new SerialIoQueue(task -> { throw failure; });
        var result = queue.submit(() -> { throw new AssertionError("rejected work ran"); });
        assertThatThrownBy(() -> result.toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
    }

    @Test
    void anObserverCannotCancelAnAcceptedWriteAndAReentrantReadIsRejected() {
        var scheduled = new LinkedBlockingQueue<Runnable>();
        var queue = new SerialIoQueue(scheduled::add);
        var value = queue.submit(() -> {
            assertThatThrownBy(() -> queue.call(() -> "nested read")).isInstanceOf(IllegalStateException.class);
            return "saved";
        });
        value.toCompletableFuture().cancel(false);
        scheduled.remove().run();
        assertThat(value.toCompletableFuture().join()).isEqualTo("saved");
    }
}
