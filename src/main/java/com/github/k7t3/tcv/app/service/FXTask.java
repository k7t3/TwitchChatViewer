package com.github.k7t3.tcv.app.service;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

public abstract class FXTask<T> extends Task<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FXTask.class);

    @Override
    protected void failed() {
        super.failed();

        LOGGER.error("error occurred into task", getException());
    }

    public void onDone(Consumer<T> consumer) {
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> consumer.accept(getValue()));
    }

    public void onDone(Runnable runnable) {
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> runnable.run());
    }

    public void waitForDone() {
        if (state() != Future.State.RUNNING)
            return;
        try {
            get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
        }
    }

    public void setSucceeded(Runnable runnable) {
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> runnable.run());
    }

    public void setFinally(Runnable runnable) {
        EventHandler<WorkerStateEvent> handler = e -> runnable.run();
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, handler);
        addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, handler);
        addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, handler);
    }

    public void runAsync() {
        TaskWorker.getInstance().submit(this);
    }

    public static <T> FXTask<T> task(Callable<T> callable) {
        return new FXTask<>() {
            @Override
            protected T call() throws Exception {
                return callable.call();
            }
        };
    }

    public static FXTask<Void> task(Runnable runnable) {
        return new FXTask<>() {
            @Override
            protected Void call() {
                runnable.run();
                return null;
            }
        };
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

    public static <T> void setOnSucceeded(FXTask<T> task, EventHandler<WorkerStateEvent> handler) {
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, handler);
    }

}
