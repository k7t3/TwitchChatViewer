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

import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import com.github.k7t3.tcv.reactive.AbstractSubscriber;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class ChatMessageSubscriber extends AbstractSubscriber<ChatMessageEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageSubscriber.class);

    private final Consumer<ChatMessageEvent> consumer;

    public ChatMessageSubscriber(Consumer<ChatMessageEvent> consumer) {
        this.consumer = consumer;
    }

    @Override
    protected void handle(ChatMessageEvent item) {
        consumer.accept(item);
    }

    @Override
    public void onNext(ChatMessageEvent item) {
        if (Platform.isFxApplicationThread()) {
            super.onNext(item);
        } else {
            Platform.runLater(() -> super.onNext(item));
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.error("error occurred", throwable);
    }

    @Override
    public void onComplete() {
        LOGGER.info("completed");
    }

}
