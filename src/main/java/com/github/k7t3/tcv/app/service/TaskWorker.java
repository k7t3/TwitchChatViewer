package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.core.ExceptionHandler;
import javafx.concurrent.WorkerStateEvent;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TaskWorker implements Closeable {

    private final ThreadFactory factory = Thread.ofVirtual().name("TCV-Worker-Thread").factory();

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(factory);

    private TaskWorker() {
    }

    void submit(FXTask<?> task) {
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> ExceptionHandler.handle(task.getException()));
        executor.submit(task);
    }

    public Executor getExecutor() {
        return executor;
    }

    @Override
    public void close() {
        executor.close();
    }

    public static TaskWorker getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final TaskWorker INSTANCE = new TaskWorker();
    }

}
