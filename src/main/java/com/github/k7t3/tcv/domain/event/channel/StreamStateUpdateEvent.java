package com.github.k7t3.tcv.domain.event.channel;

import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;

public class StreamStateUpdateEvent extends TwitchChannelEvent {

    private final StreamInfo info;

    public StreamStateUpdateEvent(TwitchChannel channel, StreamInfo info) {
        super(channel);
        this.info = info;
    }

    public StreamInfo getInfo() {
        return info;
    }
}
