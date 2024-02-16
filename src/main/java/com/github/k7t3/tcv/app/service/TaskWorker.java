package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.core.ExceptionHandler;
import javafx.concurrent.WorkerStateEvent;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskWorker implements Closeable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private TaskWorker() {
    }

    public void submit(FXTask<?> task) {
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> ExceptionHandler.handle(task.getException()));
        executor.submit(task);
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
