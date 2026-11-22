package com.github.k7t3.tcv.domain.event;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.philippheuer.events4j.api.IEventManager;
import com.github.philippheuer.events4j.api.domain.IDisposable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** SDK の購読を一つの登録世代として所有し、終了後に開始される通知を抑止する。 */
public final class EventSubscriptionScope implements AutoCloseable {
    private final Object lock = new Object();
    private final List<IDisposable> subscriptions = new ArrayList<>();
    private volatile boolean closed;

    /**
     * 所有者へ接続済みの範囲に登録する。終了済みなら登録処理自体を実行しない。
     * 失敗した場合は、この範囲だけを閉じて原因を保持する。
     * @param register SDK の購読を追加する処理
     */
    public void register(Consumer<EventSubscriptionScope> register) {
        if (closed) return;
        try {
            register.accept(this);
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * 終了時に解除する SDK 購読を追加する。終了後に返った購読も直ちに解除する。
     * @param manager 借用する SDK 発行元
     * @param type イベント型
     * @param consumer この登録世代のイベント処理
     * @param <E> イベントの型
     */
    public <E> void subscribe(IEventManager manager, Class<E> type, Consumer<E> consumer) {
        if (closed) return;
        var subscription = manager.onEvent(type, event -> {
            if (!closed) consumer.accept(event);
        });
        synchronized (lock) {
            if (!closed) {
                subscriptions.add(subscription);
                return;
            }
        }
        subscription.dispose();
    }

    @Override
    public void close() {
        final Runnable[] releases;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            releases = subscriptions.stream().map(subscription -> (Runnable) subscription::dispose).toArray(Runnable[]::new);
            subscriptions.clear();
        }
        CloseActions.runAll(releases);
    }
}
