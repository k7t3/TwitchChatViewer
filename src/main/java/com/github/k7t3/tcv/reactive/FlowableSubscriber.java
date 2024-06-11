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
