/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.EventSubscriptionScope;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.StreamStateUpdateEvent;
import com.github.k7t3.tcv.domain.event.stream.StreamStateEvent;
import java.util.concurrent.ConcurrentHashMap;

class TwitchChannelEventProvider {

    private final ConcurrentHashMap<String, TwitchChannel> channelMap = new ConcurrentHashMap<>();

    private final Twitch twitch;
    private final EventPublishers eventPublishers;

    private final Object lifecycleLock = new Object();
    private EventSubscriptionScope subscriptions;

    public TwitchChannelEventProvider(Twitch twitch, EventPublishers publishers) {
        this.twitch = twitch;
        this.eventPublishers = publishers;
    }

    public void add(TwitchChannel channel) {
        var channelId = channel.getBroadcaster().getUserId();
        channelMap.put(channelId, channel);
    }

    public void remove(TwitchChannel channel) {
        var channelId = channel.getBroadcaster().getUserId();
        channelMap.computeIfPresent(channelId, (id, current) -> current == channel ? null : current);
    }

    public void listen() {
        final EventSubscriptionScope opening;
        synchronized (lifecycleLock) {
            if (subscriptions != null) throw new IllegalStateException("channel events are already registered");
            opening = new EventSubscriptionScope();
            subscriptions = opening;
        }
        try {
            opening.register(scope -> {
                var eventManager = twitch.getClient().getEventManager();
                scope.subscribe(eventManager, StreamStateEvent.Online.class, this::onChannelGoLiveEvent);
                scope.subscribe(eventManager, StreamStateEvent.Offline.class, this::onChannelGoOfflineEvent);
                scope.subscribe(eventManager, StreamStateEvent.Viewers.class, this::onChannelViewerCountUpdateEvent);
                scope.subscribe(eventManager, StreamStateEvent.Title.class, this::onChannelChangeTitleEvent);
                scope.subscribe(eventManager, StreamStateEvent.Game.class, this::onChannelChangeGameEvent);
            });
        } catch (RuntimeException | Error failure) {
            synchronized (lifecycleLock) {
                if (subscriptions == opening) subscriptions = null;
            }
            throw failure;
        }
    }

    public void clean() {
        final EventSubscriptionScope owned;
        synchronized (lifecycleLock) {
            owned = subscriptions;
            subscriptions = null;
            channelMap.clear();
        }
        if (owned != null) owned.close();
    }

    private void onChannelGoLiveEvent(StreamStateEvent.Online e) {
        var channel = channelMap.get(e.userId());
        if (channel == null) return;

        var stream = e.stream();
        channel.setStream(stream);
        var onlineEvent = new ChannelOnlineEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }

    private void onChannelGoOfflineEvent(StreamStateEvent.Offline e) {
        var channel = channelMap.get(e.userId());
        if (channel == null) return;

        channel.setStream(null);
        var offlineEvent = new ChannelOfflineEvent(channel);
        eventPublishers.submit(offlineEvent);
    }

    private void onChannelViewerCountUpdateEvent(StreamStateEvent.Viewers e) {
        var channel = channelMap.get(e.userId());
        if (channel == null) return;

        var stream = e.stream();
        channel.setStream(stream);
        var onlineEvent = new StreamStateUpdateEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }

    private void onChannelChangeTitleEvent(StreamStateEvent.Title e) {
        var channel = channelMap.get(e.userId());
        if (channel == null) return;

        var stream = e.stream();
        channel.setStream(stream);
        var onlineEvent = new StreamStateUpdateEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }

    private void onChannelChangeGameEvent(StreamStateEvent.Game e) {
        var channel = channelMap.get(e.userId());
        if (channel == null) return;

        var stream = e.stream();
        channel.setStream(stream);
        var onlineEvent = new StreamStateUpdateEvent(channel, stream);
        eventPublishers.submit(onlineEvent);
    }
}
