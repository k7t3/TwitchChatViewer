package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import javafx.application.Platform;

public class VideoClipPostListener implements ChatRoomListener {

    private final MainViewModel viewModel;

    VideoClipPostListener(MainViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, VideoClip clip) {
        Platform.runLater(viewModel::updateClipCount);
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
