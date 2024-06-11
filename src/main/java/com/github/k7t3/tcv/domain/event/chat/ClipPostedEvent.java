package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ClipChatMessage;

public class ClipPostedEvent extends ChatRoomEvent {

    private final ClipChatMessage clipChatMessage;

    public ClipPostedEvent(ChatRoom chatRoom, ClipChatMessage clipChatMessage) {
        super(chatRoom);
        this.clipChatMessage = clipChatMessage;
    }

    public ClipChatMessage getClipChatMessage() {
        return clipChatMessage;
    }

}
