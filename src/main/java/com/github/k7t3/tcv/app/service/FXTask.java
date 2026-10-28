/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.service;

import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class FXTask<T> extends Task<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FXTask.class);
    private final CompletableFuture<T> completion = new CompletableFuture<>();

    /**
     * FX イベント配送に依存しない Future の終端通知。通知先は FX スレッドとは限らない。
     * 観測側の取消・完了操作はこのタスクに作用しない。タスクの取消は call() 内の
     * I/O の終了を意味しないため、実行中の処理の解放には所有 executor の終了待ちも必要。
     */
    public final CompletionStage<T> completion() {
        return completion.minimalCompletionStage();
    }

    /**
     * 借用タスクの取消だけを、FXイベント配送を待たず自身へ伝える。
     * cancel(true)を要求するが、observationの非割込み方針は維持する。
     * 成功・失敗・逆方向の取消は伝播しない。call内のI/O終了も意味しない。
     * 自身の終端後は、観測元の通知から自身への参照を保持し続けない。
     * @param source 取消を観測するタスク
     */
    public final void cancelWhenCancelled(FXTask<?> source) {
        Objects.requireNonNull(source);
        var target = new AtomicReference<FXTask<?>>(this);
        completion().whenComplete((ignored, failure) -> target.set(null));
        source.completion().whenComplete((ignored, failure) -> {
            if (!source.isCancelled()) return;
            var observer = target.get();
            if (observer != null) observer.cancel(true);
        });
    }

    @Override
    protected final void done() {
        try {
            completion.complete(get());
        } catch (ExecutionException failure) {
            completion.completeExceptionally(failure.getCause());
        } catch (CancellationException cancelled) {
            completion.completeExceptionally(cancelled);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            completion.completeExceptionally(interrupted);
        }
    }

    @Override
    protected void failed() {
        super.failed();

        LOGGER.error("error occurred into task", getException());
    }

    public FXTask<T> onDone(Consumer<T> consumer) {
        if (getState() == Worker.State.SUCCEEDED) consumer.accept(getValue());
        else addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> consumer.accept(getValue()));
        return this;
    }

    public FXTask<T> onDone(Runnable runnable) {
        return onDone(ignored -> runnable.run());
    }

    /** FX スレッドで失敗を受け取る。取消は失敗とせず、完了後の登録にも対応する。 */
    public FXTask<T> onFailure(Consumer<Throwable> consumer) {
        if (getState() == Worker.State.FAILED) consumer.accept(getException());
        else addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event -> consumer.accept(getException()));
        return this;
    }

    public void setFinally(Runnable runnable) {
        var called = new AtomicBoolean();
        Runnable once = () -> {
            if (called.compareAndSet(false, true)) runnable.run();
        };
        EventHandler<WorkerStateEvent> handler = e -> once.run();
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, handler);
        addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, handler);
        addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, handler);
        var state = getState();
        if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
            once.run();
        }
    }

    public static <T> FXTask<T> task(Callable<T> callable) {
        return create(callable, true);
    }

    /**
     * 別途所有する処理の結果だけを返す。取消は Future を終端にするが、結果を返す
     * ワーカーや FX スレッドを割り込まない。実処理の終了は所有者が別途追跡する。
     */
    public static <T> FXTask<T> observation(Callable<T> publication) {
        return create(publication, false);
    }

    private static <T> FXTask<T> create(Callable<T> callable, boolean interruptible) {
        return new FXTask<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return super.cancel(interruptible && mayInterruptIfRunning);
            }

            @Override
            protected T call() throws Exception {
                try { return callable.call(); }
                catch (CancellationException cancelled) {
                    cancel(false);
                    return null;
                }
            }
        };
    }

    public static FXTask<Void> task(Runnable runnable) {
        return task(() -> { runnable.run(); return null; });
    }

    public static FXTask<Void> empty() {
        var t = task(() -> {});
        t.run();
        return t;
    }

    public static <T> FXTask<T> of(T value) {
        var t = task(() -> value);
        t.run();
        return t;
    }

}
