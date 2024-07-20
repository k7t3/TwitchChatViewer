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
