package com.github.k7t3.tcv.domain.event.channel;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;

public class TwitchChannelEvent {

    private final TwitchChannel channel;

    public TwitchChannelEvent(TwitchChannel channel) {
        this.channel = channel;
    }

    public TwitchChannel getChannel() {
        return channel;
    }
}
