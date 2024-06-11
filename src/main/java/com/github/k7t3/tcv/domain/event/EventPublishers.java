package com.github.k7t3.tcv.domain.event;

import com.github.k7t3.tcv.domain.event.channel.TwitchChannelEvent;
import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomEvent;

import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.SubmissionPublisher;

public class EventPublishers implements AutoCloseable {

    /**
     * チャンネルに関するイベントのパブリッシャー
     */
    private final SubmissionPublisher<TwitchChannelEvent> channelEventPublisher = new SubmissionPublisher<>(
            ForkJoinPool.commonPool(),
            64
    );

    /**
     * チャットルームに関するイベントのパブリッシャー
     */
    private final SubmissionPublisher<ChatRoomEvent> chatEventPublisher = new SubmissionPublisher<>(
            ForkJoinPool.commonPool(),
            64
    );

    /**
     * チャットメッセージに関するパブリッシャー
     */
    private final SubmissionPublisher<ChatMessageEvent> chatMessagePublisher = new SubmissionPublisher<>(
            ForkJoinPool.commonPool(),
            256
    );

    Flow.Publisher<TwitchChannelEvent> getChannelEventPublisher() {
        return channelEventPublisher;
    }

    Flow.Publisher<ChatRoomEvent> getChatEventPublisher() {
        return chatEventPublisher;
    }

    Flow.Publisher<ChatMessageEvent> getChatMessagePublisher() {
        return chatMessagePublisher;
    }

    public void submit(TwitchChannelEvent e) {
        channelEventPublisher.submit(e);
    }

    public void submit(ChatRoomEvent e) {
        chatEventPublisher.submit(e);
    }

    public void submitChatMessage(ChatMessageEvent e) {
        chatMessagePublisher.submit(e);
    }

    @Override
    public void close() {
        channelEventPublisher.close();
        chatEventPublisher.close();
        chatMessagePublisher.close();
    }

}
