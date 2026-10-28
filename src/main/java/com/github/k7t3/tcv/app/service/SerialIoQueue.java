package com.github.k7t3.tcv.app.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * I/O を受付順に実行する。通常は借用 executor を使い、未開始ポンプを待つ背景読取は
 * 独立した仮想スレッドへ引き継ぐ。読取元は実完了まで待ち、書込は返却 stage で追跡する。
 * 観測の取消では受付済み処理を取り消さない。
 */
public final class SerialIoQueue {
    private final Object lock = new Object();
    private final Executor executor;
    private final ArrayDeque<Job<?>> jobs = new ArrayDeque<>();
    private Thread drainer;
    private boolean scheduled;
    private boolean independentScheduled;

    public SerialIoQueue(Executor executor) { this.executor = Objects.requireNonNull(executor); }

    public <T> CompletionStage<T> submit(Callable<T> action) {
        var job = new Job<>(action);
        final boolean start;
        synchronized (lock) {
            jobs.add(job);
            start = drainer == null && !scheduled;
            if (start) scheduled = true;
        }
        if (start) {
            try { executor.execute(this::drainPending); }
            catch (RuntimeException | Error failure) { rejectWaiting(failure); }
        }
        return job.result.minimalCompletionStage();
    }

    /**
     * 実行元は専用のポンプ。取消可能な読取タスクのスレッドでは書込を実行しない。
     */
    private void drainPending() {
        synchronized (lock) {
            if (drainer != null) return;
            drainer = Thread.currentThread();
            scheduled = false;
            independentScheduled = false;
        }
        while (true) {
            final Job<?> job;
            synchronized (lock) {
                job = jobs.poll();
                if (job == null) {
                    drainer = null;
                    return;
                }
            }
            job.run();
        }
    }

    /** FX 以外の読取元から、受付済み書込に続けて読み取る。I/O 処理内からの自己待機は拒否する。 */
    public <T> T call(Callable<T> action) {
        var job = new Job<>(action);
        final boolean start;
        synchronized (lock) {
            if (drainer == Thread.currentThread()) throw new IllegalStateException("reentrant serial I/O read");
            jobs.add(job);
            start = drainer == null && !independentScheduled;
            if (start) {
                scheduled = true;
                independentScheduled = true;
            }
        }
        if (start) {
            // 同じ単一 executor の未開始ポンプを待つ場合も、読取の取消を保存へ伝播させない。
            try { Thread.ofVirtual().name("TCV-Serial-Io-Read").start(this::drainPending); }
            catch (RuntimeException | Error failure) { rejectWaiting(failure); }
        }
        // 取消で戻ると未完了の読取を所有者が見失う。実処理の終端まで待ち、割込状態は保持する。
        try { return job.result.join(); }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failure.getCause());
        }
    }

    private void rejectWaiting(Throwable failure) {
        ArrayList<Job<?>> rejected;
        synchronized (lock) {
            if (drainer != null) return;
            scheduled = false;
            independentScheduled = false;
            rejected = new ArrayList<>(jobs);
            jobs.clear();
        }
        rejected.forEach(value -> value.result.completeExceptionally(failure));
    }

    private static final class Job<T> {
        final Callable<T> action;
        final CompletableFuture<T> result = new CompletableFuture<>();
        Job(Callable<T> action) { this.action = Objects.requireNonNull(action); }
        void run() {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }
    }
}
