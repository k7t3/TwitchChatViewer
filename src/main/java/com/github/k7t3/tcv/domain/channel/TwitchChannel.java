package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.events.*;
import com.github.twitch4j.helix.domain.ChatBadgeSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TwitchChannel {

    private final Broadcaster broadcaster;

    private final AtomicReference<StreamInfo> streamRef;

    private List<ChatBadgeSet> badgeSets = null;

    private final AtomicBoolean badgeLoaded = new AtomicBoolean(false);

    private final Twitch twitch;

    private final ExecutorService eventExecutor;

    private final CopyOnWriteArraySet<TwitchChannelListener> listeners = new CopyOnWriteArraySet<>();

    private boolean following = false;

    public TwitchChannel(Twitch twitch, ExecutorService eventExecutor, Broadcaster broadcaster, StreamInfo stream) {
        this.twitch = twitch;
        this.eventExecutor = eventExecutor;
        this.broadcaster = broadcaster;
        this.streamRef = new AtomicReference<>(stream);
    }

    private List<IDisposable> eventSubs;

    void updateEventSubs() {
        clearEventSubs();

        var client = twitch.getClient();
        var eventManager = client.getEventManager();

        eventSubs = new ArrayList<>();

        eventSubs.add(
                eventManager.onEvent(ChannelGoLiveEvent.class, e -> {
                    var stream = StreamInfo.of(e.getStream());
                    setStream(stream);
                    for (var listener : listeners)
                        eventExecutor.submit(() -> listener.onOnline(stream));
                })
        );
        eventSubs.add(
                eventManager.onEvent(ChannelGoOfflineEvent.class, e -> {
                    setStream(null);
                    for (var listener : listeners)
                        eventExecutor.submit(listener::onOffline);
                })
        );
        eventSubs.add(
                eventManager.onEvent(ChannelViewerCountUpdateEvent.class, e -> {
                    var stream = StreamInfo.of(e.getStream());
                    setStream(stream);
                    for (var listener : listeners)
                        eventExecutor.submit(() -> listener.onViewerCountUpdated(stream));
                })
        );
        eventSubs.add(
                eventManager.onEvent(ChannelChangeTitleEvent.class, e -> {
                    var stream = StreamInfo.of(e.getStream());
                    setStream(stream);
                    for (var listener : listeners)
                        eventExecutor.submit(() -> listener.onTitleChanged(stream));
                })
        );
        eventSubs.add(
                eventManager.onEvent(ChannelChangeGameEvent.class, e -> {
                    var stream = StreamInfo.of(e.getStream());
                    setStream(stream);
                    for (var listener : listeners)
                        eventExecutor.submit(() -> listener.onGameChanged(stream));
                })
        );
    }

    private void clearEventSubs() {
        if (eventSubs == null) {
            return;
        }

        for (var sub : eventSubs) {
            sub.dispose();
        }
        eventSubs.clear();
    }

    public void addListener(TwitchChannelListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TwitchChannelListener listener) {
        listeners.remove(listener);
    }

    public void loadBadgesIfNotLoaded() {
        if (badgeLoaded.get()) return;

        var client = twitch.getClient();
        var token = twitch.getAccessToken();
        var helix = client.getHelix();

        // チャンネルのバッジを取得
        var badgeCommand = helix.getChannelChatBadges(token, broadcaster.getUserId());
        var badges = badgeCommand.execute().getBadgeSets();

        badgeSets = new ArrayList<>(badges);

        badgeLoaded.set(true);
    }

    private ChatRoom chatRoom;

    public ChatRoom getChatRoom() {
        if (chatRoom != null) return chatRoom;

        chatRoom = new ChatRoom(twitch, eventExecutor, broadcaster);
        chatRoom.listen();
        return chatRoom;
    }

    public void leaveChat() {
        if (chatRoom == null) return;

        chatRoom.leave();

        var repository = twitch.getChannelRepository();

        // フォローされていないチャンネルはチャットを使い終わった時点でクリアする
        if (!isFollowing()) {

            repository.releaseChannel(this);

            clearEventSubs();
        }

        chatRoom = null;
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    public String getChannelName() {
        return broadcaster.getUserLogin();
    }

    public void setStream(StreamInfo stream) {
        this.streamRef.set(stream);
    }

    public StreamInfo getStream() {
        return streamRef.get();
    }

    public boolean isStreaming() {
        return streamRef.get() != null;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public boolean isFollowing() {
        return following;
    }

    public Optional<String> getBadgeUrl(ChatBadge badge) {
        if (!badgeLoaded.get()) throw new IllegalStateException("badges have not been loaded");

        for (var badgeSet : badgeSets) {
            if (!badgeSet.getSetId().equalsIgnoreCase(badge.id())) continue;

            for (var b : badgeSet.getVersions()) {
                if (!b.getId().equalsIgnoreCase(badge.version())) continue;

                return Optional.of(b.getSmallImageUrl());
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return "TwitchChannel{" +
                "broadcaster=" + broadcaster +
                ", stream=" + streamRef +
                '}';
    }
}
