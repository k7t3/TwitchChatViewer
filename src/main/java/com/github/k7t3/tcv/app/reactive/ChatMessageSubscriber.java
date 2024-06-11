package com.github.k7t3.tcv.app.reactive;

import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import com.github.k7t3.tcv.reactive.AbstractSubscriber;
import com.github.k7t3.tcv.reactive.BackPressurePolicy;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class ChatMessageSubscriber extends AbstractSubscriber<ChatMessageEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMessageSubscriber.class);

    private final Consumer<ChatMessageEvent> consumer;

    public ChatMessageSubscriber(Consumer<ChatMessageEvent> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        super.onSubscribe(subscription);
    }

    @Override
    protected void handle(ChatMessageEvent item) {
        consumer.accept(item);
    }

    @Override
    public void onNext(ChatMessageEvent item) {
        if (Platform.isFxApplicationThread()) {
            onNextFX(item);
        } else {
            Platform.runLater(() -> onNextFX(item));
        }
    }

    private void onNextFX(ChatMessageEvent item) {
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
    public void onError(Throwable throwable) {
        LOGGER.error("error occurred", throwable);
    }

    @Override
    public void onComplete() {
        LOGGER.info("completed");
    }

}
