package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;

public class VideoClipListener implements ChatRoomListener {

    private final Broadcaster broadcaster;

    private final VideoClipRepository repository;

    public VideoClipListener(VideoClipRepository repository, Broadcaster broadcaster) {
        this.repository = repository;
        this.broadcaster = broadcaster;
    }

    @Override
    public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, VideoClip clip) {
        repository.posted(broadcaster, clip);
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

}
