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
