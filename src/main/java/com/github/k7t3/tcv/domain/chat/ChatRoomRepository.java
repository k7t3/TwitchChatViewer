package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.chat.event.IRCMessageEventPublisher;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.Flow;

public class ChatRoomRepository implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomRepository.class);

    private final Twitch twitch;

    private final IRCMessageEventPublisher publisher;

    public ChatRoomRepository(Twitch twitch) {
        this.twitch = twitch;

        publisher = new IRCMessageEventPublisher();
    }

    public void load() {
        var client = twitch.getClient();
        var handler = client.getEventManager().getEventHandler(SimpleEventHandler.class);
        handler.onEvent(ChannelMessageEvent.class, event -> {
//            LOGGER.info("ChannelMessageEvent {}", event);
            publisher.push(event.getMessageEvent());
        });
    }

    public Flow.Publisher<ChatData> getChatPublisher() {
        return publisher;
    }

    public void joinChat(Broadcaster broadcaster) {
        LOGGER.info("join chat {}", broadcaster);
        var chat = twitch.getClient().getChat();
        chat.joinChannel(broadcaster.getUserLogin());
    }

    public void leaveChat(Broadcaster broadcaster) {
        LOGGER.info("leave chat {}", broadcaster);
        var chat = twitch.getClient().getChat();
        chat.leaveChannel(broadcaster.getUserLogin());
    }

    @Override
    public void close() {
        publisher.close();
    }
}
