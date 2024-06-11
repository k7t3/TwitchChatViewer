package com.github.k7t3.tcv.domain.event;

import com.github.k7t3.tcv.domain.event.channel.TwitchChannelEvent;
import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomEvent;
import com.github.k7t3.tcv.reactive.DownCastSubscriber;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class EventSubscribers {

    private final EventPublishers publishers;

    public EventSubscribers(EventPublishers publishers) {
        this.publishers = publishers;
    }

    public void subscribeChannelEvent(Flow.Subscriber<TwitchChannelEvent> subscriber) {
        publishers.getChannelEventPublisher().subscribe(subscriber);
    }

    public void subscribeChatEvent(Flow.Subscriber<ChatRoomEvent> subscriber) {
        publishers.getChatEventPublisher().subscribe(subscriber);
    }

    public void subscribeMessageEvent(Flow.Subscriber<ChatMessageEvent> subscriber) {
        publishers.getChatMessagePublisher().subscribe(subscriber);
    }

}
