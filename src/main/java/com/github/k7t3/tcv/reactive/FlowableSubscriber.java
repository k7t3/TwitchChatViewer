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

import java.util.concurrent.Flow;

/**
 * 流量制限をサポートするサブスクライバー
 * @param <T> 購読するタイプ
 */
public interface FlowableSubscriber<T> extends Flow.Subscriber<T> {

    /**
     * 購読を解除する。
     * <p>
     *     まだ購読が開始されていない({@link #onSubscribe(Flow.Subscription)}がコールされていない)
     *     ときは何も実行されない。
     * </p>
     */
    void cancel();

    /**
     * 流量制限ポリシーを指定する。
     * @param policy 指定する流量制限ポリシー
     */
    void setBackPressurePolicy(BackPressurePolicy policy);

}
