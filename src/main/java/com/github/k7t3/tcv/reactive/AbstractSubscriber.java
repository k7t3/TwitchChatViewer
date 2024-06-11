package com.github.k7t3.tcv.reactive;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流量制限を設定できるサブスクライバの基本クラス。
 * <p>
 *     {@link #setBackPressurePolicy(BackPressurePolicy)}メソッドで
 *     流量制限を指定できる。
 * </p>
 * @param <T> 購読するタイプ
 */
public abstract class AbstractSubscriber<T> implements FlowableSubscriber<T> {

    protected final AtomicReference<BackPressurePolicy> backPressurePolicy = new AtomicReference<>(BackPressurePolicy.FULL);
    protected final AtomicReference<BackPressurePolicy> requestedPolicy = new AtomicReference<>(BackPressurePolicy.FULL);
    protected final AtomicLong segmentCounter = new AtomicLong(0);

    private Flow.Subscription subscription;

    /**
     * 流量制限を指定する。
     * @param policy 指定するポリシー
     */
    public void setBackPressurePolicy(BackPressurePolicy policy) {
        backPressurePolicy.set(policy);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;

        var policy = backPressurePolicy.get();
        requestedPolicy.set(policy);
        request(policy.getSegmentSize());
    }

    protected abstract void handle(T item);

    protected void request(long requestSize) {
        segmentCounter.set(0);
        subscription.request(requestSize);
    }

    @Override
    public void onNext(T item) {
        handle(item);

        // 現在設定されているポリシーとリクエスト時点のポリシーを取得
        var policy = backPressurePolicy.get();
        var requested = requestedPolicy.getAndSet(policy);

        // ポリシーが変更されているときは再度リクエスト
        if (policy != requested) {
            request(policy.getSegmentSize());
            return;
        }

        // FULLでないときは処理件数を考慮して適宜リクエストを再発行する
        if (policy != BackPressurePolicy.FULL) {
            if (segmentCounter.incrementAndGet() == policy.getSegmentSize()) {
                request(policy.getSegmentSize());
            }
        }
    }

    @Override
    public void cancel() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }
}
