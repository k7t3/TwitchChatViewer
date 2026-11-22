package com.github.k7t3.tcv.domain;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.chat.TwitchChat;
import java.util.Objects;

/** このセッションで使うSDKモジュールと、その所有者への終了要求。 */
public final class TwitchClients implements AutoCloseable {
    private final EventManager events;
    private final HelixClient helix;
    private final TwitchChat chat;
    private final Runnable release;

    TwitchClients(EventManager events, HelixClient helix, TwitchChat chat, Runnable release) {
        this.events = Objects.requireNonNull(events);
        this.helix = Objects.requireNonNull(helix);
        this.chat = Objects.requireNonNull(chat);
        this.release = Objects.requireNonNull(release);
    }

    public EventManager getEventManager() { return events; }
    public HelixClient getHelix() { return helix; }
    public TwitchChat getChat() { return chat; }
    @Override public void close() { release.run(); }
}
