package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;

import java.util.List;

public class ChannelFinder {

    private final Twitch twitch;

    public ChannelFinder(Twitch twitch) {
        this.twitch = twitch;
    }

    public List<FoundChannel> search(String startsWith, boolean liveOnly) {

        var api = twitch.getTwitchAPI();
        return api.search(startsWith, liveOnly);

    }
}
