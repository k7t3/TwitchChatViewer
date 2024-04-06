package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import javafx.application.Platform;

class TwitchChannelStreamListener implements TwitchChannelListener {

    private final TwitchChannelViewModel viewModel;

    public TwitchChannelStreamListener(TwitchChannelViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void onOnline(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> viewModel.updateStreamInfo(info));
    }

    @Override
    public void onOffline(TwitchChannel channel) {
        Platform.runLater(() -> viewModel.updateStreamInfo(null));
    }

    @Override
    public void onViewerCountUpdated(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> viewModel.updateStreamInfo(info));
    }

    @Override
    public void onTitleChanged(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> viewModel.updateStreamInfo(info));
    }

    @Override
    public void onGameChanged(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> viewModel.updateStreamInfo(info));
    }

}
