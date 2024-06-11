package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;

public class ChatClearedEvent extends ChatRoomEvent {

    public ChatClearedEvent(ChatRoom chatRoom) {
        super(chatRoom);
    }

}
