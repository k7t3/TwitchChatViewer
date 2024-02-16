package com.github.k7t3.tcv.domain.channel;

public interface TwitchChannelListener {

    void onOnline(StreamInfo info);

    void onOffline();

    void onViewerCountUpdated(StreamInfo info);

    void onTitleChanged(StreamInfo info);

    void onGameChanged(StreamInfo info);

}
