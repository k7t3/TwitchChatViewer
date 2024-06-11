package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;

public abstract class ChatRoomEvent {

    private final ChatRoom chatRoom;

    public ChatRoomEvent(ChatRoom chatRoom) {
        this.chatRoom = chatRoom;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

}
