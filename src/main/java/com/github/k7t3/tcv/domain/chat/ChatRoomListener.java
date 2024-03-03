package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.clip.VideoClip;

public interface ChatRoomListener {

    void onChatDataPosted(ChatRoom chatRoom, ChatData item);

    void onClipPosted(ChatRoom chatRoom, VideoClip clip);

    void onChatCleared(ChatRoom chatRoom);

    void onChatMessageDeleted(ChatRoom chatRoom, String messageId);

    void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active);

    void onRaidReceived(ChatRoom chatRoom, String raiderName, int viewerCount);

    void onUserSubscribed(ChatRoom chatRoom, String userName);

    void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName);

    ChatRoomListener DEFAULT = new ChatRoomListener() {
        @Override
        public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
        }

        @Override
        public void onClipPosted(ChatRoom chatRoom, VideoClip clip) {
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
        public void onUserSubscribed(ChatRoom chatRoom, String userName) {
        }

        @Override
        public void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName) {
        }
    };

}
