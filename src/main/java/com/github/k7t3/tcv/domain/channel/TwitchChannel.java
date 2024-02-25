package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.clip.VideoClipListener;
import com.github.k7t3.tcv.domain.core.EventExecutorWrapper;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.events.*;
import com.github.twitch4j.helix.domain.ChatBadgeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TwitchChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchChannel.class);

    private final Broadcaster broadcaster;

    private final AtomicReference<StreamInfo> streamRef;

    private List<ChatBadgeSet> badgeSets = null;

    private final AtomicBoolean badgeLoaded = new AtomicBoolean(false);

    private final Twitch twitch;

    private final EventExecutorWrapper eventExecutor;

    private final CopyOnWriteArraySet<TwitchChannelListener> listeners = new CopyOnWriteArraySet<>();

    private boolean following = false;

    public TwitchChannel(
            Twitch twitch,
            EventExecutorWrapper eventExecutor,
            Broadcaster broadcaster,
            StreamInfo stream
    ) {
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

        eventSubs.add(eventManager.onEvent(ChannelGoLiveEvent.class, this::onChannelGoLiveEvent));
        eventSubs.add(eventManager.onEvent(ChannelGoOfflineEvent.class, this::onChannelGoOfflineEvent));
        eventSubs.add(eventManager.onEvent(ChannelViewerCountUpdateEvent.class, this::onChannelViewerCountUpdateEvent));
        eventSubs.add(eventManager.onEvent(ChannelChangeTitleEvent.class, this::onChannelChangeTitleEvent));
        eventSubs.add(eventManager.onEvent(ChannelChangeGameEvent.class, this::onChannelChangeGameEvent));
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

    private void onChannelChangeGameEvent(ChannelChangeGameEvent e) {
        if (!e.getChannel().getId().equalsIgnoreCase(broadcaster.getUserId())) {
            return;
        }

        var stream = StreamInfo.of(e.getStream());
        setStream(stream);
        for (var listener : listeners)
            eventExecutor.submit(() -> listener.onGameChanged(stream));
    }

    private void onChannelChangeTitleEvent(ChannelChangeTitleEvent e) {
        if (!e.getChannel().getId().equalsIgnoreCase(broadcaster.getUserId())) {
            return;
        }

        var stream = StreamInfo.of(e.getStream());
        setStream(stream);
        for (var listener : listeners)
            eventExecutor.submit(() -> listener.onTitleChanged(stream));
    }

    private void onChannelViewerCountUpdateEvent(ChannelViewerCountUpdateEvent e) {
        if (!e.getChannel().getId().equalsIgnoreCase(broadcaster.getUserId())) {
            return;
        }

        var stream = StreamInfo.of(e.getStream());
        setStream(stream);
        for (var listener : listeners)
            eventExecutor.submit(() -> listener.onViewerCountUpdated(stream));
    }

    private void onChannelGoOfflineEvent(ChannelGoOfflineEvent e) {
        if (!e.getChannel().getId().equalsIgnoreCase(broadcaster.getUserId())) {
            return;
        }

        setStream(null);
        for (var listener : listeners)
            eventExecutor.submit(listener::onOffline);
    }

    private void onChannelGoLiveEvent(ChannelGoLiveEvent e) {
        if (!e.getChannel().getId().equalsIgnoreCase(broadcaster.getUserId())) {
            return;
        }

        var stream = StreamInfo.of(e.getStream());
        setStream(stream);
        for (var listener : listeners)
            eventExecutor.submit(() -> listener.onOnline(stream));
    }

    public void addListener(TwitchChannelListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TwitchChannelListener listener) {
        listeners.remove(listener);
    }

    public void loadBadgesIfNotLoaded() {
        if (badgeLoaded.get()) return;

        var api = twitch.getTwitchAPI();
        badgeSets = api.getChannelBadgeSet(broadcaster);

        badgeLoaded.set(true);
    }

    private ChatRoom chatRoom;

    private VideoClipListener clipListener;

    public ChatRoom getChatRoom() {
        if (chatRoom != null) return chatRoom;
        LOGGER.info("{} chat room created", getChannelName());

        chatRoom = new ChatRoom(twitch, eventExecutor, broadcaster);
        chatRoom.listen();

        clipListener = new VideoClipListener(twitch.getClipRepository(), broadcaster);
        chatRoom.addListener(clipListener);

        return chatRoom;
    }

    public void leaveChat() {
        if (chatRoom == null) return;

        chatRoom.removeListener(clipListener);
        chatRoom.leave();

        // フォローされていないチャンネルはチャットを使い終わった時点でクリアする
        if (!isFollowing()) {

            var repository = twitch.getChannelRepository();
            repository.releaseChannel(this);

            clearEventSubs();
        }

        chatRoom = null;
    }

    void clear() {
        // チャットを使用している場合は閉じる
        leaveChat();

        // チャンネル監視イベントをクリア
        clearEventSubs();

        listeners.clear();
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

                return Optional.of(b.getMediumImageUrl());
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
