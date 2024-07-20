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

package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.event.AppEvent;
import de.saxsys.mvvmfx.utils.notifications.NotificationObserver;
import org.slf4j.LoggerFactory;

/**
 * {@link NotificationObserver}を型安全に利用できるようにしたインターフェース
 * <p>
 *     {@link NotificationObserver#receivedNotification(String, Object...)}メソッドは
 *     Object[]型のpayload変数で任意の個数のパラメータを受け取ることができたが、
 *     このインターフェースにおいては任意の型のイベントインスタンスのみを受け取ることができる。
 * </p>
 * <p>
 *     {@link NotificationObserver#receivedNotification(String, Object...)}メソッドの
 *     payloadが空であるときは{@link #receivedNotification(AppEvent)}メソッドにはnullが
 *     渡され、そうでないときは先頭の要素が渡される(複数の要素があるときは警告がログに出力される)。
 * </p>
 * @param <T> 購読するイベントのタイプ
 */
@FunctionalInterface
public interface TypeSafeNotificationObserver<T extends AppEvent> extends NotificationObserver {

    /**
     * 発行されたイベントを受け取るメソッド
     * <p>
     *     {@link NotificationObserver#receivedNotification(String, Object...)}メソッドの
     *     payloadが空であるときはnullが渡され、そうでないときは
     *     先頭の要素が渡される(複数の要素があるときは警告がログに出力される)。
     * </p>
     * @param event イベント
     */
    void receivedNotification(T event);

    @SuppressWarnings("unchecked")
    default void receivedNotification(String key, Object... payload) {
        if (payload == null || payload.length < 1) {
            receivedNotification(null);
            return;
        }

        if (payload.length != 1) {
            LoggerFactory.getLogger(TypeSafeNotificationObserver.class).warn("too many parameters");
            return;
        }

        try {
            T typeSafe = (T) payload[0];
            receivedNotification(typeSafe);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
