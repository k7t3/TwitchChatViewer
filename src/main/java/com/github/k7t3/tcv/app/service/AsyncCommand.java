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

import de.saxsys.mvvmfx.utils.commands.Command;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;

public class AsyncCommand<T> extends Service<T> implements Command {

    private final BooleanProperty executable = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper notExecutable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper notRunning = new ReadOnlyBooleanWrapper(false);

    private final Callable<T> callable;

    public AsyncCommand(Callable<T> callable) {
        this.callable = callable;
        notExecutable.bind(executable);
        notRunning.bind(runningProperty());
        setExecutor(TaskWorker.getInstance().getExecutor());
    }

    @Override
    public void execute() {
        if (isNotExecutable()) throw new IllegalStateException("not executable");
        if (isRunning()) throw new IllegalStateException("running");
        restart();
    }

    @Override
    public boolean isExecutable() { return executable.get(); }
    @Override
    public ReadOnlyBooleanProperty executableProperty() { return executable; }
    @Override
    public boolean isNotExecutable() { return notExecutable.get(); }
    @Override
    public ReadOnlyBooleanProperty notExecutableProperty() { return notExecutable.getReadOnlyProperty(); }
    @Override
    public boolean isNotRunning() { return super.isRunning(); }
    @Override
    public ReadOnlyBooleanProperty notRunningProperty() { return notRunning.getReadOnlyProperty(); }

    @Override
    protected Task<T> createTask() {
        return new FXTask<>() {
            @Override
            protected T call() throws Exception {
                return callable.call();
            }
        };
    }
}
