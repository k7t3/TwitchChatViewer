package com.github.k7t3.tcv.domain.chat.event;

import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessageParser;
import com.github.twitch4j.chat.events.channel.IRCMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

public class IRCMessageEventPublisher implements Flow.Publisher<ChatData>, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IRCMessageEventPublisher.class);

    private final ChatMessageParser parser = new ChatMessageParser();

    private final CopyOnWriteArrayList<NonBlockingSubscription> subscriptions = new CopyOnWriteArrayList<>();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public IRCMessageEventPublisher() {
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ChatData> subscriber) {
        var subscription = new NonBlockingSubscription(subscriber);
        subscriber.onSubscribe(subscription);
        subscriptions.add(subscription);
    }

    public void push(IRCMessageEvent item) {
        executor.submit(() -> {
            try {
                handle(item);
            } catch (Exception e) {
                LOGGER.error("Error occurred into IRCMessage handling", e);
                onErrorSubscribers(e);
            }
        });
    }

    private void handle(IRCMessageEvent item) {
        var channelId = item.getChannelId();
        if (item.getChannelId() == null || item.getChannelId().isEmpty()) {
            LOGGER.warn("Channel ID is null. {}", item);
            return;
        }

        var channelName = item.getChannelName().orElse("");
        var message = item.getMessage().orElse(null);
        var emotes = item.getTagValue("emotes").orElse(null);

        var chatMessage = parser.parse(message, emotes);
        var userId = item.getUserId();
        var userDisplayName = item.getUserDisplayName().orElse(null);
        var userName = item.getUserName();
        var userColor = item.getUserChatColor().orElse(null);
        var badges = item.getBadges()
                .entrySet()
                .stream()
                .map(pair -> new ChatBadge(pair.getKey(), pair.getValue()))
                .toList();

        var data = new ChatData(channelId, channelName, userId, userDisplayName, userName, userColor, badges, chatMessage);

        // サブスクライバに送信
        for (var subscription : subscriptions) {
            subscription.push(data);
        }
    }

    private void onErrorSubscribers(Throwable throwable) {
        for (var subscription : subscriptions) {
            subscription.onError(throwable);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    public static class NonBlockingSubscription implements Flow.Subscription {

        private final Flow.Subscriber<? super ChatData> subscriber;

        private boolean done = false;

        private final AtomicLong request = new AtomicLong(0);

        public NonBlockingSubscription(Flow.Subscriber<? super ChatData> subscriber) {
            this.subscriber = subscriber;
        }

        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
            done = true;
        }

        public void push(ChatData item) {
            if (request.get() < 1) return;

            subscriber.onNext(item);

            // MAX_VALUEのときは無制限
            if (request.get() != Long.MAX_VALUE) {
                // リクエストを一つ消化
                request.decrementAndGet();
            }
        }

        @Override
        public void request(long n) {
            if (done) return;
            if (n < 1) {
                subscriber.onError(new IllegalArgumentException());
                done = true;
                return;
            }

            if (n == Long.MAX_VALUE) {
                request.set(Long.MAX_VALUE);
            } else {
                // TODO オーバーフロー対策
                request.addAndGet(n);
            }
        }

        @Override
        public void cancel() {
            done = true;
            subscriber.onComplete();
        }

    }
}
