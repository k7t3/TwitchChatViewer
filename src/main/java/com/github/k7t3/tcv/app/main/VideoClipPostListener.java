package com.github.k7t3.tcv.app.main;

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
    public void onChatDataPosted(ChatData item) {
    }

    @Override
    public void onClipPosted(VideoClip clip) {
        Platform.runLater(viewModel::updateClipCount);
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
