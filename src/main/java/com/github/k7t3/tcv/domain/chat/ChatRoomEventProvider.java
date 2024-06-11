package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.chat.*;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.chat.events.channel.*;
import com.github.twitch4j.chat.events.roomstate.EmoteOnlyEvent;
import com.github.twitch4j.chat.events.roomstate.FollowersOnlyEvent;
import com.github.twitch4j.chat.events.roomstate.SlowModeEvent;
import com.github.twitch4j.chat.events.roomstate.SubscribersOnlyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoomEventProvider {

    private final ConcurrentHashMap<String, ChatRoom> chatRoomMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<ChatRoomState>> stateMap = new ConcurrentHashMap<>();

    private final Twitch twitch;
    private final EventPublishers eventPublishers;
    private final ChatMessageParser parser = new ChatMessageParser();
    private final ClipFinder clipFinder;

    private List<IDisposable> subscriptions;

    public ChatRoomEventProvider(Twitch twitch, EventPublishers eventPublishers) {
        this.twitch = twitch;
        this.eventPublishers = eventPublishers;
        this.clipFinder = new ClipFinder(twitch);
    }

    public void onJoined(ChatRoom chatRoom) {
        var channelId = chatRoom.getBroadcaster().getUserId();
        chatRoomMap.put(channelId, chatRoom);
        stateMap.put(channelId, ConcurrentHashMap.newKeySet());
    }

    public void onLeft(ChatRoom chatRoom) {
        var channelId = chatRoom.getBroadcaster().getUserId();
        chatRoomMap.remove(channelId);
        stateMap.remove(channelId);
    }

    public void listen() {
        if (subscriptions != null) throw new IllegalStateException();

        var chat = twitch.getChat();
        var eventManager = chat.getEventManager();

        subscriptions = new ArrayList<>();
        subscriptions.add(eventManager.onEvent(ChannelMessageEvent.class, this::onChannelMessageEvent));
        subscriptions.add(eventManager.onEvent(SubscriptionEvent.class, this::onSubscriptionEvent));
        subscriptions.add(eventManager.onEvent(ClearChatEvent.class, this::onClearChatEvent));
        subscriptions.add(eventManager.onEvent(DeleteMessageEvent.class, this::onDeleteMessageEvent));
        subscriptions.add(eventManager.onEvent(EmoteOnlyEvent.class, this::onEmoteOnlyEvent));
        subscriptions.add(eventManager.onEvent(FollowersOnlyEvent.class, this::onFollowersOnlyEvent));
        subscriptions.add(eventManager.onEvent(RaidEvent.class, this::onRaidEvent));
        subscriptions.add(eventManager.onEvent(SlowModeEvent.class, this::onSlowModeEvent));
        subscriptions.add(eventManager.onEvent(SubscribersOnlyEvent.class, this::onSubscribersOnlyEvent));
        subscriptions.add(eventManager.onEvent(CheerEvent.class, this::onCheerEvent));
    }

    public void clean() {
        if (subscriptions == null || subscriptions.isEmpty()) return;
        for (var sub : subscriptions)
            sub.dispose();
        subscriptions.clear();
    }

    private void updateRoomState(String channelId, ChatRoomState roomState, boolean active) {
        var chatRoom = chatRoomMap.get(channelId);
        if (chatRoom == null) return;
        var roomStates = stateMap.get(channelId);
        if (roomStates == null) return;

        if (active) {
            roomStates.add(roomState);
        } else if (roomStates.contains(roomState)) {
            roomStates.remove(roomState);
        } else {
            return;
        }

        var event = new ChatRoomStateUpdatedEvent(chatRoom, roomState, active);
        eventPublishers.submit(event);
    }

    private ChatData parseMessageEvent(IRCMessageEvent item) {
        var channelId = item.getChannelId();
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
        var firedAt = item.getFiredAtInstant();

        return new ChatData(
                channelId,
                channelName,
                msgId,
                userId,
                userDisplayName,
                userName,
                userColor,
                badges,
                chatMessage,
                firedAt
        );
    }

    private void onChannelMessageEvent(ChannelMessageEvent e) {
        var chatRoom = chatRoomMap.get(e.getChannel().getId());
        if (chatRoom == null) return;

        var message = e.getMessage();

        var chatData = parseMessageEvent(e.getMessageEvent());
        var postedEvent = new ChatMessageEvent(chatRoom, chatData);
        eventPublishers.submitChatMessage(postedEvent);

        var clipOp = clipFinder.findClip(message);
        clipOp.ifPresent(clipChatMessage -> {
            var clipPostedEvent = new ClipPostedEvent(chatRoom, clipChatMessage);
            eventPublishers.submit(clipPostedEvent);
        });
    }

    private void onSubscriptionEvent(SubscriptionEvent e) {
        var chatRoom = chatRoomMap.get(e.getChannel().getId());
        if (chatRoom == null) return;

        var user = e.getUser();

        // ギフトのときはギフターとレシーバーを扱うだけ
        if (e.getGifted()) {
            var giver = e.getGiftedBy();
            var giftedEvent = new UserGiftedSubscribeEvent(chatRoom, giver.getName(), user.getName());
            eventPublishers.submit(giftedEvent);
        } else {
            var chatData = parseMessageEvent(e.getMessageEvent());
            var postedEvent = new ChatMessageEvent(chatRoom, chatData);
            eventPublishers.submit(postedEvent);
        }
    }

    private void onClearChatEvent(ClearChatEvent e) {
        var chatRoom = chatRoomMap.get(e.getChannel().getId());
        if (chatRoom == null) return;

        var clearedEvent = new ChatClearedEvent(chatRoom);
        eventPublishers.submit(clearedEvent);
    }

    private void onDeleteMessageEvent(DeleteMessageEvent e) {
        var chatRoom = chatRoomMap.get(e.getChannel().getId());
        if (chatRoom == null) return;

        var deletedEvent = new ChatMessageDeletedEvent(chatRoom, e.getMsgId());
        eventPublishers.submit(deletedEvent);
    }

    private void onEmoteOnlyEvent(EmoteOnlyEvent e) {
        updateRoomState(e.getChannel().getId(), ChatRoomState.EMOTE_ONLY, e.isActive());
    }

    private void onFollowersOnlyEvent(FollowersOnlyEvent e) {
        updateRoomState(e.getChannel().getId(), ChatRoomState.FOLLOWERS_ONLY, e.isActive());
    }

    private void onSlowModeEvent(SlowModeEvent e) {
        updateRoomState(e.getChannel().getId(), ChatRoomState.SLOW_MODE, e.isActive());
    }

    private void onSubscribersOnlyEvent(SubscribersOnlyEvent e) {
        updateRoomState(e.getChannel().getId(), ChatRoomState.SUBSCRIBERS_ONLY, e.isActive());
    }

    private void onRaidEvent(RaidEvent e) {
        var chatRoom = chatRoomMap.get(e.getChannel().getId());
        if (chatRoom == null) return;

        var raider = e.getRaider();
        var viewerCount = e.getViewers();

        var raidReceivedEvent = new RaidReceivedEvent(chatRoom, raider.getName(), viewerCount);
        eventPublishers.submit(raidReceivedEvent);
    }

    private void onCheerEvent(CheerEvent event) {
        var chatRoom = chatRoomMap.get(event.getChannel().getId());
        if (chatRoom == null) return;

        var chatData = parseMessageEvent(event.getMessageEvent());
        var bits = event.getBits();

        var cheer = new ChatCheer(chatData, bits);
        var cheerEvent = new CheeredEvent(chatRoom, cheer);
        eventPublishers.submit(cheerEvent);
    }

}
