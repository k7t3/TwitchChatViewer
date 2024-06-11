package com.github.k7t3.tcv.domain.event.channel;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;

public class ChannelOfflineEvent extends TwitchChannelEvent {

    public ChannelOfflineEvent(TwitchChannel channel) {
        super(channel);
    }

}
