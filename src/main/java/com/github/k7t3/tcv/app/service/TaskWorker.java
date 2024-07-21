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
