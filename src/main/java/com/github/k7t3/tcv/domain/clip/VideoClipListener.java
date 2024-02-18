package com.github.k7t3.tcv.domain.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.VideoClip;
import com.github.k7t3.tcv.domain.chat.ChatData;
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
    public void onChatDataPosted(ChatData item) {
    }

    @Override
    public void onClipPosted(VideoClip clip) {
        repository.posted(broadcaster, clip);
    }

    @Override
    public void onChatCleared() {
    }

    @Override
    public void onChatMessageDeleted(String messageId) {
    }

    @Override
    public void onStateUpdated(ChatRoomState roomState, boolean active) {
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

}
