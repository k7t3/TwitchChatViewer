package com.github.k7t3.tcv.app.command;

import de.saxsys.mvvmfx.utils.commands.Command;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 非同期で実行できるコマンド。
 * <p>
 *     実装された{@link Callable#call()}メソッドの内容が非同期で実行され、
 *     その処理が終了するまでNestedEventLoop内でブロックされる。
 * </p>
 * @param <T> 返却されるタイプ
 */
public abstract class AsyncCommand<T> implements Command, Callable<T> {

    // NestedEventLoopで使用するキー
    private final Object loopKey = new Object();

    protected final ReadOnlyBooleanWrapper executable = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper notExecutable = new ReadOnlyBooleanWrapper();

    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper notRunning = new ReadOnlyBooleanWrapper();

    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper();

    private final AtomicReference<T> value = new AtomicReference<>();
    private final AtomicReference<Throwable> throwable = new AtomicReference<>();
    private final AtomicBoolean succeeded = new AtomicBoolean();

    public AsyncCommand() {
        notExecutable.bind(executable.not());
        notRunning.bind(running.not());
    }

    @Override
    public void execute() {
        if (!isExecutable()) throw new IllegalStateException("not executable");
        if (isRunning()) throw new IllegalStateException("running");
        if (!Platform.canStartNestedEventLoop())
            throw new IllegalStateException("can not start nested event loop");

        running.set(true);
        throwable.set(null);
        value.set(null);
        succeeded.set(false);

        // 非同期処理の開始
        CompletableFuture.runAsync(this::run);

        // 実行した非同期処理が終了するまでブロック
        enterEventLoop();

        running.set(false);
    }

    private void enterEventLoop() {
        Platform.enterNestedEventLoop(loopKey);
    }

    private void exitEventLoop() {
        Platform.exitNestedEventLoop(loopKey, null);
    }

    private void run() {
        try {
            var v = call();
            value.set(v);
            succeeded.set(true);
        } catch (Exception e) {
            throwable.set(e);
        } finally {
            Platform.runLater(this::exitEventLoop);
        }
    }

    public boolean isSucceeded() {
        return succeeded.get();
    }

    public T getValue() {
        return value.get();
    }

    public boolean hasThrowable() {
        return throwable.get() != null;
    }

    public Throwable getThrowable() {
        return throwable.get();
    }

    @Override
    public boolean isExecutable() {
        return executable.get();
    }

    @Override
    public ReadOnlyBooleanProperty executableProperty() {
        return executable.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty notExecutableProperty() {
        return notExecutable.getReadOnlyProperty();
    }

    @Override
    public boolean isNotExecutable() {
        return notExecutable.get();
    }

    protected void updateProgress(double progress) {
        var p = Math.clamp(progress, 0, 1);
        if (Platform.isFxApplicationThread()) {
            this.progress.set(p);
        } else {
            Platform.runLater(() -> this.progress.set(p));
        }
    }

    @Override
    public double getProgress() {
        return progress.get();
    }

    @Override
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    @Override
    public boolean isNotRunning() {
        return notRunning.get();
    }

    @Override
    public ReadOnlyBooleanProperty notRunningProperty() {
        return notRunning.getReadOnlyProperty();
    }

}