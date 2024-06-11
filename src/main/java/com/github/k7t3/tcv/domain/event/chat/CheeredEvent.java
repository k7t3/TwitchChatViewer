package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatCheer;
import com.github.k7t3.tcv.domain.chat.ChatRoom;

public class CheeredEvent extends ChatRoomEvent {

    private final ChatCheer cheer;

    public CheeredEvent(ChatRoom chatRoom, ChatCheer cheer) {
        super(chatRoom);
        this.cheer = cheer;
    }

    public ChatCheer getCheer() {
        return cheer;
    }
}
