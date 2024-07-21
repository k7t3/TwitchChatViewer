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

package com.github.k7t3.tcv.reactive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 購読対象のタイプをダウンキャストしたタイプを購読するサブスクライバ
 * @param <R> 実際に解決するダウンキャストしたタイプ
 * @param <T> 購読するタイプ
 */
@SuppressWarnings("unchecked")
public abstract class DownCastSubscriber<T, R extends T> extends AbstractSubscriber<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownCastSubscriber.class);

    private final Class<R> type;

    public DownCastSubscriber(Class<R> type) {
        this.type = type;
    }

    protected abstract void handleCasted(R item);

    @Override
    protected final void handle(T item) {
        if (type.isAssignableFrom(item.getClass())) {
            handleCasted((R) item);
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
