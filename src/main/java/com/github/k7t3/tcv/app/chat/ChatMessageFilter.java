package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.chat.ChatData;

import java.util.function.Predicate;

/**
 * チャットのメッセージフィルタ
 */
public interface ChatMessageFilter extends Predicate<ChatData> {

    byte[] serialize();

    ChatMessageFilter DEFAULT = new ChatMessageFilter() {
        @Override
        public byte[] serialize() {
            return new byte[0];
        }

        @Override
        public boolean test(ChatData chatData) {
            return true;
        }
    };

}
