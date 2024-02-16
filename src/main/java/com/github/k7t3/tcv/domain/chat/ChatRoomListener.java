package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.channel.VideoClip;

public interface ChatRoomListener {

    void onChatDataPosted(ChatData item);

    void onClipPosted(VideoClip clip);

    void onChatCleared();

    void onChatMessageDeleted(String messageId);

    void onStateUpdated(ChatRoomState roomState);

    void onRaidReceived(String raiderName, int viewerCount);

    void onUserSubscribed(String userName);

    void onUserGiftedSubscribe(String giverName, String userName);

    ChatRoomListener DEFAULT = new ChatRoomListener() {
        @Override
        public void onChatDataPosted(ChatData item) {
        }

        @Override
        public void onClipPosted(VideoClip clip) {
        }

        @Override
        public void onChatCleared() {
        }

        @Override
        public void onChatMessageDeleted(String messageId) {
        }

        @Override
        public void onStateUpdated(ChatRoomState roomState) {
        }

        @Override
        public void onRaidReceived(String raiderName, int viewerCount) {
        }

        @Override
        public void onUserSubscribed(String userName) {
        }

        @Override
        public void onUserGiftedSubscribe(String giverName, String userName) {
        }
    };

}
