package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatRoomRepository implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomRepository.class);

    private final Twitch twitch;

    private final ExecutorService eventExecutor;

    private final List<ChatRoomEventListener> listeners;

    public ChatRoomRepository(Twitch twitch) {
        this.twitch = twitch;

        eventExecutor = Executors.newVirtualThreadPerTaskExecutor();
        listeners = new ArrayList<>();
    }

    public void joinChat(ChatRoomEventListener listener) {
        var broadcaster = listener.getBroadcaster();
        LOGGER.info("join chat {}", broadcaster);

        listener.listen(eventExecutor, twitch.getClient().getEventManager());

        var chat = twitch.getClient().getChat();
        chat.joinChannel(broadcaster.getUserLogin());

        listeners.add(listener);
    }

    public void leaveChat(ChatRoomEventListener listener) {
        var broadcaster = listener.getBroadcaster();
        LOGGER.info("leave chat {}", broadcaster);

        var chat = twitch.getClient().getChat();
        chat.leaveChannel(broadcaster.getUserLogin());

        listener.cancel();

        listeners.remove(listener);
    }

    @Override
    public void close() {
        eventExecutor.close();

        for (var listener : listeners)
            listener.cancel();

        listeners.clear();
    }
}
