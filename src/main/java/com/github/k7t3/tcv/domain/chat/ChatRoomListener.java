package com.github.k7t3.tcv.domain.chat;

public interface ChatRoomListener {

    void onChatDataPosted(ChatRoom chatRoom, ChatData item);

    void onClipPosted(ChatRoom chatRoom, ClipChatMessage clipChatMessage);

    void onChatCleared(ChatRoom chatRoom);

    void onChatMessageDeleted(ChatRoom chatRoom, String messageId);

    void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active);

    void onRaidReceived(ChatRoom chatRoom, String raiderName, int viewerCount);

    void onUserSubscribed(ChatRoom chatRoom, ChatData chatData);

    void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName);

    void onCheered(ChatRoom chatRoom, ChatCheer cheer);

}
