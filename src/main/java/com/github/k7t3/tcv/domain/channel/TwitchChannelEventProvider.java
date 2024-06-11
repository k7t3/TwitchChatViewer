package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.StreamStateUpdateEvent;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.events.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class TwitchChannelEventProvider {

    private final ConcurrentHashMap<String, TwitchChannel> channelMap = new ConcurrentHashMap<>();

    private final Twitch twitch;
    private final EventPublishers eventPublishers;

    private List<IDisposable> subscriptions;

    public TwitchChannelEventProvider(Twitch twitch, EventPublishers publishers) {
        this.twitch = twitch;
        this.eventPublishers = publishers;
    }

    public void add(TwitchChannel channel) {
        var channelId = channel.getChannelName();
        channelMap.put(channelId, channel);
    }

    public void remove(TwitchChannel channel) {
        var channelId = channel.getChannelName();
        channelMap.remove(channelId);
    }

    public void listen() {
        if (subscriptions != null) throw new IllegalStateException();

        var client = twitch.getClient();
        var eventManager = client.getEventManager();

        subscriptions = new ArrayList<>();
        subscriptions.add(eventManager.onEvent(ChannelGoLiveEvent.class, this::onChannelGoLiveEvent));
        subscriptions.add(eventManager.onEvent(ChannelGoOfflineEvent.class, this::onChannelGoOfflineEvent));
        subscriptions.add(eventManager.onEvent(ChannelViewerCountUpdateEvent.class, this::onChannelViewerCountUpdateEvent));
        subscriptions.add(eventManager.onEvent(ChannelChangeTitleEvent.class, this::onChannelChangeTitleEvent));
        subscriptions.add(eventManager.onEvent(ChannelChangeGameEvent.class, this::onChannelChangeGameEvent));
    }

    public void clean() {
        if (subscriptions == null || subscriptions.isEmpty()) return;
        for (var sub : subscriptions)
            sub.dispose();
        subscriptions.clear();
    }

    private void onChannelGoLiveEvent(ChannelGoLiveEvent e) {
        var channel = channelMap.get(e.getChannel().getId());
        if (channel == null) return;

        var stream = StreamInfo.of(e.getStream());
        var onlineEvent = new ChannelOnlineEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }

    private void onChannelGoOfflineEvent(ChannelGoOfflineEvent e) {
        var channel = channelMap.get(e.getChannel().getId());
        if (channel == null) return;

        var offlineEvent = new ChannelOfflineEvent(channel);
        eventPublishers.submit(offlineEvent);
    }

    private void onChannelViewerCountUpdateEvent(ChannelViewerCountUpdateEvent e) {
        var channel = channelMap.get(e.getChannel().getId());
        if (channel == null) return;

        var stream = StreamInfo.of(e.getStream());
        var onlineEvent = new StreamStateUpdateEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }

    private void onChannelChangeTitleEvent(ChannelChangeTitleEvent e) {
        var channel = channelMap.get(e.getChannel().getId());
        if (channel == null) return;

        var stream = StreamInfo.of(e.getStream());
        var onlineEvent = new StreamStateUpdateEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }

    private void onChannelChangeGameEvent(ChannelChangeGameEvent e) {
        var channel = channelMap.get(e.getChannel().getId());
        if (channel == null) return;

        var stream = StreamInfo.of(e.getStream());
        var onlineEvent = new StreamStateUpdateEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }
}
