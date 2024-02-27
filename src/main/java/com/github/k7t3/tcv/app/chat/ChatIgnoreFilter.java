package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.chat.ChatData;

import java.util.function.Predicate;

/**
 * チャットを無視する条件のフィルタ
 */
public interface ChatIgnoreFilter extends Predicate<ChatData> {

    byte[] serialize();

    ChatIgnoreFilter DEFAULT = new ChatIgnoreFilter() {
        @Override
        public byte[] serialize() {
            return new byte[0];
        }

        @Override
        public boolean test(ChatData chatData) {
            return false;
        }
    };

}
