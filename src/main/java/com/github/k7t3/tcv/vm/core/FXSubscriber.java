package com.github.k7t3.tcv.vm.core;

import com.github.k7t3.tcv.view.core.ExceptionHandler;
import javafx.application.Platform;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class FXSubscriber<T> implements Flow.Subscriber<T> {

    private Flow.Subscription subscription;

    private final AtomicBoolean done = new AtomicBoolean(false);

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    public void dispose() {
        if (done.get()) return;
        subscription.cancel();
        subscription = null;
        done.set(true);
    }

    @Override
    public void onNext(T item) {
        if (done.get()) return;
        handleOnBackground(item);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        done.set(true);
        Platform.runLater(() -> ExceptionHandler.handle(throwable));
    }

    @Override
    public void onComplete() {
        done.set(true);
    }

    protected abstract void handleOnBackground(T item);

}
