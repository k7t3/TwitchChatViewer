package com.github.k7t3.tcv.app.reactive;

import com.github.k7t3.tcv.reactive.DownCastSubscriber;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class DownCastFXSubscriber<T, R extends T> extends DownCastSubscriber<T, R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownCastFXSubscriber.class);

    private final String typeName;
    private final Consumer<R> consumer;

    public DownCastFXSubscriber(Class<R> type, Consumer<R> consumer) {
        super(type);
        this.typeName = type.getSimpleName();
        this.consumer = consumer;
    }

    @Override
    protected void handleCasted(R item) {
        if (Platform.isFxApplicationThread()) {
            consumer.accept(item);
        } else {
            Platform.runLater(() -> consumer.accept(item));
        }
    }

    @Override
    public void onComplete() {
        LOGGER.info("{} completed", typeName);
    }
}
