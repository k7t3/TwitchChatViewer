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

import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.core.WindowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 確定した座標を受付順に保存する。画面終了や観測取消でも受付済みの書込は完了させる。 */
public class WindowBoundsService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowBoundsService.class);
    private final Object lifecycle = new Object();
    private final WindowBoundsEntityService service;
    private final Executor executor;
    private final SerialIoQueue io;
    private final Consumer<CompletionStage<Void>> tracker;
    private final Consumer<Throwable> failures;
    private final Set<Read> reads = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public WindowBoundsService(WindowBoundsEntityService service, Executor executor) {
        this(service, executor, ignored -> {});
    }

    public WindowBoundsService(WindowBoundsEntityService service, Executor executor, Consumer<CompletionStage<Void>> tracker) {
        this(service, executor, tracker, failure -> LOGGER.error("window bounds write failed", failure));
    }

    WindowBoundsService(WindowBoundsEntityService service, Executor executor,
                        Consumer<CompletionStage<Void>> tracker, Consumer<Throwable> failures) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
        this.tracker = Objects.requireNonNull(tracker);
        this.failures = Objects.requireNonNull(failures);
        io = new SerialIoQueue(executor);
    }

    public FXTask<Void> saveBoundsAsync(String identity, WindowBounds bounds) {
        var entity = new WindowBoundsEntity(identity, bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.maximized());
        var write = new Write();
        synchronized (lifecycle) {
            checkOpen();
            try {
                tracker.accept(write.drained.minimalCompletionStage());
                io.submit(() -> { service.save(entity); return null; })
                        .whenComplete((ignored, failure) -> write.finish(unwrap(failure)));
            } catch (RuntimeException | Error failure) { write.finish(failure); }
        }
        return write.result;
    }

    public WindowBounds getBounds(String identity) {
        checkOpen();
        return readBounds(identity);
    }

    private WindowBounds readBounds(String identity) {
        return io.call(() -> {
            var entity = service.get(identity);
            return new WindowBounds(entity.x(), entity.y(), entity.width(), entity.height(), entity.maximized());
        });
    }

    /** 取得失敗の表示は現在の窓が所有する。サービス終了は全ての未採用読取を取り消す。 */
    public FXTask<WindowBounds> getBoundsAsync(String identity) {
        final Read read;
        synchronized (lifecycle) {
            checkOpen();
            read = new Read(identity);
            reads.add(read);
            read.result.completion().whenComplete((ignored, failure) -> {
                reads.remove(read);
                if (read.result.isCancelled()) read.worker.cancel(true);
            });
            read.worker.completion().whenComplete((value, failure) -> read.finish(value, unwrap(failure)));
        }
        try { executor.execute(read.worker); }
        catch (RuntimeException | Error rejected) {
            read.finish(null, rejected);
            read.worker.cancel(true);
        }
        return read.result;
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("window bounds service is closed");
    }

    @Override public void close() {
        final List<Read> owned;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            owned = List.copyOf(reads);
            reads.clear();
        }
        CloseActions.runAll(owned.stream().map(read -> (Runnable) () -> read.result.cancel(true)).toArray(Runnable[]::new));
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Error error) throw error;
        if (failure instanceof Exception exception) throw exception;
        if (failure != null) throw new IllegalStateException(failure);
    }

    private final class Write {
        final CompletableFuture<Void> drained = new CompletableFuture<>();
        Throwable failure;
        final FXTask<Void> result = FXTask.observation(() -> { rethrow(failure); return null; });
        void finish(Throwable failure) {
            this.failure = failure;
            try { result.run(); }
            finally {
                if (failure == null) drained.complete(null);
                else {
                    drained.completeExceptionally(failure);
                    failures.accept(failure);
                }
            }
        }
    }

    private final class Read {
        WindowBounds value;
        Throwable failure;
        final FXTask<WindowBounds> worker;
        final FXTask<WindowBounds> result = FXTask.observation(() -> {
            if (closed) throw new CancellationException("window bounds read was retired");
            rethrow(failure);
            return value;
        });
        Read(String identity) { worker = FXTask.task(() -> readBounds(identity)); }
        void finish(WindowBounds value, Throwable failure) {
            if (closed || failure instanceof CancellationException) { result.cancel(false); return; }
            this.value = value;
            this.failure = failure;
            result.run();
        }
    }
}
