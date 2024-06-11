package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;

public class ChatMessageDeletedEvent extends ChatRoomEvent {

    private final String deletedMessageId;

    public ChatMessageDeletedEvent(ChatRoom chatRoom, String deletedMessageId) {
        super(chatRoom);
        this.deletedMessageId = deletedMessageId;
    }

    public String getDeletedMessageId() {
        return deletedMessageId;
    }

}
