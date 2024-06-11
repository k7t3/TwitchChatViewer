package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;

public class UserGiftedSubscribeEvent extends ChatRoomEvent {

    private final String giverName;

    private final String receiverName;

    public UserGiftedSubscribeEvent(ChatRoom chatRoom, String giverName, String receiverName) {
        super(chatRoom);
        this.giverName = giverName;
        this.receiverName = receiverName;
    }

    public String getGiverName() {
        return giverName;
    }

    public String getReceiverName() {
        return receiverName;
    }
}
