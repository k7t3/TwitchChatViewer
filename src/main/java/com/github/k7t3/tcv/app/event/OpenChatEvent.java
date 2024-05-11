package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;

import java.util.Collections;
import java.util.List;

/**
 * チャットを開いたときのイベント
 */
public class OpenChatEvent extends Event {

    private final List<TwitchChannelViewModel> channels;

    public OpenChatEvent(List<TwitchChannelViewModel> channels) {
        this.channels = Collections.unmodifiableList(channels);
    }

    /**
     * チャットが開かれたチャンネルのリストを返す
     * @return チャットが開かれたチャンネルのリスト
     */
    public List<TwitchChannelViewModel> getChannels() {
        return channels;
    }

}
