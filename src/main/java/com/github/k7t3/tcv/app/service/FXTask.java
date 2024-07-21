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

    public FXTask<T> onDone(Consumer<T> consumer) {
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> consumer.accept(getValue()));
        return this;
    }

    public FXTask<T> onDone(Runnable runnable) {
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> runnable.run());
        return this;
    }

    public void waitForDone() {
        if (state() != Future.State.RUNNING)
            return;
        try {
            get(20, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.warn("failed to await for done", e);
        }
    }

    public void setFinally(Runnable runnable) {
        EventHandler<WorkerStateEvent> handler = e -> runnable.run();
        addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, handler);
        addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, handler);
        addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, handler);
    }

    public FXTask<T> runAsync() {
        TaskWorker.getInstance().submit(this);
        return this;
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

}
