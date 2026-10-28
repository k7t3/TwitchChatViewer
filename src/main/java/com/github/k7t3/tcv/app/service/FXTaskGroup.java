package com.github.k7t3.tcv.app.service;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** FX で開始する複数の処理について、すべての Future の終端と失敗を回収する。 */
public final class FXTaskGroup {
    private FXTaskGroup() {}

    /**
     * task の取消は子の処理へ伝播しない。settled はその取消後も全件の終端を待ち、
     * 実際の失敗を保持する。取消だけの場合の settled は正常完了する。
     * 子が取消された時点で I/O が終了したとは限らないため、executor の終了待ちも必要。
     */
    public record Batch(FXTask<Void> task, CompletionStage<Void> settled) {}

    /**
     * 呼び出しスレッド上で全件の開始を試みる。開始中の失敗でも後続を中断しない。
     * 完了の監視は FX イベントや追加の executor スロットを使わない。
     */
    public static <T> Batch startAll(Collection<T> items, Function<? super T, ? extends FXTask<?>> starter) {
        var snapshot = List.copyOf(items);
        Objects.requireNonNull(starter);
        var collector = new Collector(snapshot.size());
        for (int i = 0; i < snapshot.size(); i++) {
            int index = i;
            try {
                var task = Objects.requireNonNull(starter.apply(snapshot.get(i)), "starter returned no task");
                task.completion().whenComplete((ignored, failure) -> collector.completed(index, failure));
            } catch (RuntimeException | Error failure) {
                collector.completed(index, failure);
            }
        }
        if (snapshot.isEmpty()) collector.finish();
        return new Batch(collector.task, collector.settled.minimalCompletionStage());
    }

    private static final class Collector {
        private final Throwable[] outcomes;
        private final AtomicInteger remaining;
        private final CompletableFuture<Void> settled = new CompletableFuture<>();
        private final FXTask<Void> task = FXTask.observation(this::result);
        private Throwable failure;
        private boolean cancelled;

        Collector(int size) {
            outcomes = new Throwable[size];
            remaining = new AtomicInteger(size);
        }

        void completed(int index, Throwable error) {
            while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
            outcomes[index] = error;
            if (remaining.decrementAndGet() == 0) finish();
        }

        void finish() {
            var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
            for (var outcome : outcomes) {
                if (outcome instanceof CancellationException) cancelled = true;
                else if (outcome != null && seen.add(outcome)) {
                    if (failure == null) {
                        failure = outcome;
                        Collections.addAll(seen, failure.getSuppressed());
                    } else failure.addSuppressed(outcome);
                }
            }
            task.run();
            if (failure == null) settled.complete(null);
            else settled.completeExceptionally(failure);
        }

        private Void result() throws Exception {
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new IllegalStateException(failure);
            if (cancelled) throw new CancellationException("a child task was cancelled");
            return null;
        }
    }
}
