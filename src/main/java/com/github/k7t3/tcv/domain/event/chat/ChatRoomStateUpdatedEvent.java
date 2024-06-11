package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;

public class ChatRoomStateUpdatedEvent extends ChatRoomEvent {

    private final ChatRoomState state;

    private final boolean active;

    public ChatRoomStateUpdatedEvent(ChatRoom chatRoom, ChatRoomState state, boolean active) {
        super(chatRoom);
        this.state = state;
        this.active = active;
    }

    public ChatRoomState getState() {
        return state;
    }

    public boolean isActive() {
        return active;
    }
}
