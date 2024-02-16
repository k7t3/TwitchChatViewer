package com.github.k7t3.tcv.app.service;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public abstract class FXTask<T> extends Task<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FXTask.class);

    @Override
    protected void failed() {
        super.failed();

        LOGGER.error("error occurred into task", getException());
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
        return task(() -> {});
    }

    public static <T> FXTask<T> of(T value) {
        return task(() -> value);
    }

    public static <T> void setOnSucceeded(FXTask<T> task, EventHandler<WorkerStateEvent> handler) {
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, handler);
    }

    public static <T> void setOnFinished(FXTask<T> task, EventHandler<WorkerStateEvent> handler) {
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, handler);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, handler);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, handler);
    }

}
