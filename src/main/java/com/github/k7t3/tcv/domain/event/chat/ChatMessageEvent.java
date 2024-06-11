package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;

public class ChatMessageEvent extends ChatRoomEvent {

    private final ChatData chatData;

    public ChatMessageEvent(ChatRoom chatRoom, ChatData chatData) {
        super(chatRoom);
        this.chatData = chatData;
    }

    public ChatData getChatData() {
        return chatData;
    }

}
