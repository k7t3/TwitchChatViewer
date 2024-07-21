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
