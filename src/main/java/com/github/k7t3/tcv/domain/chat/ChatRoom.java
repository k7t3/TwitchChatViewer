package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatRoom {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoom.class);

    private final Broadcaster broadcaster;

    private final TwitchChannel channel;

    private final Twitch twitch;

    public ChatRoom(Twitch twitch, Broadcaster broadcaster, TwitchChannel channel) {
        this.twitch = twitch;
        this.channel = channel;
        this.broadcaster = broadcaster;

        var chat = twitch.getChat();
        chat.joinChannel(getBroadcaster().getUserLogin());
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    public void leave() {
        LOGGER.info("{} leave chat", getBroadcaster().getUserLogin());

        var chat = twitch.getChat();
        chat.leaveChannel(getBroadcaster().getUserLogin());
    }

}
