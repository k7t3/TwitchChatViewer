package com.github.k7t3.tcv.domain.channel;

public interface TwitchChannelListener {

    void onOnline(TwitchChannel channel, StreamInfo info);

    void onOffline(TwitchChannel channel);

    void onViewerCountUpdated(TwitchChannel channel, StreamInfo info);

    void onTitleChanged(TwitchChannel channel, StreamInfo info);

    void onGameChanged(TwitchChannel channel, StreamInfo info);

}
