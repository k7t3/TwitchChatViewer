package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ClipFinder;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.chat.events.AbstractChannelEvent;
import com.github.twitch4j.chat.events.channel.*;
import com.github.twitch4j.chat.events.roomstate.EmoteOnlyEvent;
import com.github.twitch4j.chat.events.roomstate.FollowersOnlyEvent;
import com.github.twitch4j.chat.events.roomstate.SlowModeEvent;
import com.github.twitch4j.chat.events.roomstate.SubscribersOnlyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;

public class ChatRoom {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoom.class);

    private final Broadcaster broadcaster;

    private final String channelId;

    private final ChatMessageParser parser = new ChatMessageParser();

    private final ClipFinder clipFinder;

    private ChatRoomState roomState = ChatRoomState.NORMAL;

    private final CopyOnWriteArraySet<ChatRoomListener> listeners = new CopyOnWriteArraySet<>();

    private final Twitch twitch;

    private final ExecutorService eventExecutor;

    public ChatRoom(Twitch twitch, ExecutorService eventExecutor, Broadcaster broadcaster) {
        this.twitch = twitch;
        this.eventExecutor = eventExecutor;
        this.broadcaster = broadcaster;
        this.channelId = broadcaster.getUserId();
        this.clipFinder = new ClipFinder(twitch);
    }

    public void addListener(ChatRoomListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ChatRoomListener listener) {
        this.listeners.remove(listener);
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    private List<IDisposable> subscriptions;

    public void listen() {
        LOGGER.info("{} chat room event listening started", getBroadcaster().getUserLogin());

        subscriptions = new ArrayList<>();

        var chat = twitch.getChat();
        var eventManager = chat.getEventManager();

        subscriptions.add(eventManager.onEvent(ChannelMessageEvent.class, this::onChannelMessageEvent));
        subscriptions.add(eventManager.onEvent(SubscriptionEvent.class, this::onSubscriptionEvent));
        subscriptions.add(eventManager.onEvent(ClearChatEvent.class, this::onClearChatEvent));
        subscriptions.add(eventManager.onEvent(DeleteMessageEvent.class, this::onDeleteMessageEvent));
        subscriptions.add(eventManager.onEvent(EmoteOnlyEvent.class, this::onEmoteOnlyEvent));
        subscriptions.add(eventManager.onEvent(FollowersOnlyEvent.class, this::onFollowersOnlyEvent));
        subscriptions.add(eventManager.onEvent(RaidEvent.class, this::onRaidEvent));
        subscriptions.add(eventManager.onEvent(SlowModeEvent.class, this::onSlowModeEvent));
        subscriptions.add(eventManager.onEvent(SubscribersOnlyEvent.class, this::onSubscribersOnlyEvent));

        chat.joinChannel(getBroadcaster().getUserLogin());
    }

    public void leave() {
        if (subscriptions == null) return;
        LOGGER.info("{} leave chat", getBroadcaster().getUserLogin());

        var chat = twitch.getChat();
        chat.leaveChannel(getBroadcaster().getUserLogin());

        for (var subscription : subscriptions) {
            subscription.dispose();
        }

        subscriptions.clear();
        subscriptions = null;
    }

    private boolean notMyEvent(AbstractChannelEvent channel) {
        return !channelId.equalsIgnoreCase(channel.getChannel().getId());
    }

    private void onChannelMessageEvent(ChannelMessageEvent event) {
        if (notMyEvent(event)) return;
        eventExecutor.submit(() -> {
            var chatData = parseMessageEvent(event.getMessageEvent());
            for (var listener : listeners)
                listener.onChatDataPosted(chatData);
        });
        var message = event.getMessage();
        eventExecutor.submit(() -> {
            var clipOp = clipFinder.findClip(message);
            if (clipOp.isPresent()) {
                for (var listener : listeners)
                    listener.onClipPosted(clipOp.get());
            }
        });
    }

    private ChatData parseMessageEvent(IRCMessageEvent item) {
        var channelName = item.getChannelName().orElse("");
        var message = item.getMessage().orElse(null);
        var emotes = item.getTagValue("emotes").orElse(null);

        var msgId = item.getMessageId().orElse("");
        var chatMessage = parser.parse(message, emotes);
        var userId = item.getUserId();
        var userDisplayName = item.getUserDisplayName().orElse(null);
        var userName = item.getUserName();
        var userColor = item.getUserChatColor().orElse(null);
        var badges = item.getBadges()
                .entrySet()
                .stream()
                .map(pair -> new ChatBadge(pair.getKey(), pair.getValue()))
                .toList();

        return new ChatData(channelId, channelName, msgId, userId, userDisplayName, userName, userColor, badges, chatMessage);
    }

    private void onClearChatEvent(ClearChatEvent event) {
        if (notMyEvent(event)) return;
        eventExecutor.submit(() -> {
            for (var listener : listeners)
                listener.onChatCleared();
        });
    }

    private void onDeleteMessageEvent(DeleteMessageEvent event) {
        if (notMyEvent(event)) return;
        eventExecutor.submit(() -> {
            for (var listener : listeners)
                listener.onChatMessageDeleted(event.getMessage());
        });
    }

    private void updateRoomState(ChatRoomState roomState, boolean active) {
        if (active) {
            this.roomState = roomState;
        }
        else if (this.roomState == roomState) {
            this.roomState = ChatRoomState.NORMAL;
        }
        else {
            return;
        }

        var state = this.roomState;
        eventExecutor.submit(() -> {
            for (var listener : listeners)
                listener.onStateUpdated(state);
        });
    }

    private void onEmoteOnlyEvent(EmoteOnlyEvent event) {
        if (notMyEvent(event)) return;
        updateRoomState(ChatRoomState.EMOTE_ONLY, event.isActive());
    }

    private void onFollowersOnlyEvent(FollowersOnlyEvent event) {
        if (notMyEvent(event)) return;
        updateRoomState(ChatRoomState.FOLLOWERS_ONLY, event.isActive());
    }

    private void onSlowModeEvent(SlowModeEvent event) {
        if (notMyEvent(event)) return;
        updateRoomState(ChatRoomState.SLOW_MODE, event.isActive());
    }

    private void onSubscribersOnlyEvent(SubscribersOnlyEvent event) {
        if (notMyEvent(event)) return;
        updateRoomState(ChatRoomState.SUBSCRIBERS_ONLY, event.isActive());
    }

    private void onRaidEvent(RaidEvent event) {
        if (notMyEvent(event)) return;
        var raider = event.getRaider();
        var viewerCount = event.getViewers();
        eventExecutor.submit(() -> {
            for (var listener : listeners)
                listener.onRaidReceived(raider.getName(), viewerCount);
        });
    }

    private void onSubscriptionEvent(SubscriptionEvent event) {
        if (notMyEvent(event)) return;

        var user = event.getUser();

        if (event.getGifted()) {
            var giver = event.getGiftedBy();
            eventExecutor.submit(() -> {
                for (var listener : listeners)
                    listener.onUserGiftedSubscribe(giver.getName(), user.getName());
            });
        } else {
            eventExecutor.submit(() -> {
                for (var listener : listeners)
                    listener.onUserSubscribed(user.getName());
            });
        }
    }

}
