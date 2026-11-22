package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.awt.Desktop;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 一つの画面・モデルが開始するブラウザ操作を所有する。入力は FX 上で固定する。 */
public final class BrowserActions implements AutoCloseable {
    @FunctionalInterface interface Opener { boolean open(URI uri) throws Exception; }
    private final Executor executor;
    private final Consumer<CompletionStage<Void>> tracker;
    private final Consumer<Throwable> errors;
    private final Opener opener;
    private final Set<Opening> pending = new HashSet<>();
    private volatile boolean closed;
    private volatile long generation;

    public BrowserActions(Executor executor, Consumer<CompletionStage<Void>> tracker, Consumer<Throwable> errors) {
        this(executor, tracker, errors, uri -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
            desktop.browse(uri);
            return true;
        });
    }

    BrowserActions(Executor executor, Consumer<CompletionStage<Void>> tracker, Consumer<Throwable> errors, Opener opener) {
        this.executor = Objects.requireNonNull(executor);
        this.tracker = Objects.requireNonNull(tracker);
        this.errors = Objects.requireNonNull(errors);
        this.opener = Objects.requireNonNull(opener);
    }

    public FXTask<Boolean> open(String url) { return openAll(List.of(url)); }

    public FXTask<Boolean> openAll(List<String> urls) {
        if (closed) throw new IllegalStateException("browser actions are closed");
        var links = List.copyOf(urls).stream().map(URI::create).toList();
        var opening = new Opening(links);
        pending.add(opening);
        opening.worker.cancelWhenCancelled(opening.result);
        opening.result.cancelWhenCancelled(opening.worker);
        opening.result.onFailure(failure -> { if (opening.isCurrent()) errors.accept(failure); });
        opening.result.setFinally(() -> { pending.remove(opening); opening.worker.cancel(true); });
        opening.worker.onDone(value -> { opening.value = value; opening.result.run(); });
        opening.worker.onFailure(failure -> { opening.failure = failure; opening.result.run(); });
        try {
            tracker.accept(opening.drained.minimalCompletionStage());
            executor.execute(() -> {
                try { opening.worker.run(); }
                finally { opening.drained.complete(null); }
            });
        } catch (RuntimeException | Error failure) {
            opening.failure = failure;
            opening.result.run();
            opening.worker.cancel(true);
            opening.drained.complete(null);
        }
        return opening.result;
    }

    /** 現在の操作を取り消す。次のログイン等で同じ所有者を再利用できる。 */
    public void cancelAll() {
        generation++;
        var owned = List.copyOf(pending);
        pending.clear();
        CloseActions.runAll(owned.stream().map(opening -> (Runnable) () -> opening.result.cancel(true)).toArray(Runnable[]::new));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        cancelAll();
    }

    private final class Opening {
        final long token = generation;
        final CompletableFuture<Void> drained = new CompletableFuture<>();
        final FXTask<Boolean> worker;
        final FXTask<Boolean> result;
        Boolean value;
        Throwable failure;
        Opening(List<URI> links) {
            worker = FXTask.task(() -> {
                boolean supported = true;
                for (var uri : links) {
                    if (closed || generation != token || isCancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException("browser scope ended");
                    supported &= opener.open(uri);
                }
                return supported;
            });
            result = FXTask.observation(() -> {
                if (!isCurrent()) throw new CancellationException("browser opening was retired");
                if (failure instanceof Error error) throw error;
                if (failure instanceof Exception exception) throw exception;
                return value;
            });
        }
        boolean isCancelled() { return result.isCancelled(); }
        boolean isCurrent() { return !closed && generation == token && pending.contains(this) && !result.isCancelled(); }
    }
}
