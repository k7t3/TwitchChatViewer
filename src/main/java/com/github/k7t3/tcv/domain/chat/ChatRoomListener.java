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

    ChatRoomListener DEFAULT = new ChatRoomListener() {
        @Override
        public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
        }

        @Override
        public void onClipPosted(ChatRoom chatRoom, ClipChatMessage clipChatMessage) {
        }

        @Override
        public void onChatCleared(ChatRoom chatRoom) {
        }

        @Override
        public void onChatMessageDeleted(ChatRoom chatRoom, String messageId) {
        }

        @Override
        public void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active) {
        }

        @Override
        public void onRaidReceived(ChatRoom chatRoom, String raiderName, int viewerCount) {
        }

        @Override
        public void onUserSubscribed(ChatRoom chatRoom, ChatData chatData) {
        }

        @Override
        public void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName) {
        }
    };

}
