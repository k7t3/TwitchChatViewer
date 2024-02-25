package com.github.k7t3.tcv.domain.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class EventExecutorWrapper implements Closeable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void submit(Runnable runnable) {
        try {
            executor.submit(runnable);
        } catch (RejectedExecutionException ignored) {
            // 終了時に発生する例外を抑制
        }
    }

    @Override
    public void close() throws IOException {
        executor.close();
    }
}
