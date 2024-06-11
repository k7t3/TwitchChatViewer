package com.github.k7t3.tcv.domain.event.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoom;

public class RaidReceivedEvent extends ChatRoomEvent {

    private final String raiderName;

    private final int viewerCount;

    public RaidReceivedEvent(ChatRoom chatRoom, String raiderName, int viewerCount) {
        super(chatRoom);
        this.raiderName = raiderName;
        this.viewerCount = viewerCount;
    }

    public String getRaiderName() {
        return raiderName;
    }

    public int getViewerCount() {
        return viewerCount;
    }
}
