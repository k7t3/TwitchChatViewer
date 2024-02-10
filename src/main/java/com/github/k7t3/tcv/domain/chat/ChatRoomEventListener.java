package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.chat.events.AbstractChannelEvent;
import com.github.twitch4j.chat.events.channel.*;
import com.github.twitch4j.chat.events.roomstate.*;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class ChatRoomEventListener {

    private final Broadcaster broadcaster;

    private final String channelId;

    private final ChatMessageParser parser = new ChatMessageParser();

    public ChatRoomEventListener(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.channelId = broadcaster.getUserId();
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    private ExecutorService executor;

    private List<IDisposable> subscriptions;

    void listen(ExecutorService executor, EventManager eventManager) {
        this.executor = executor;
        subscriptions = new ArrayList<>();

        subscriptions.add(eventManager.onEvent(ChannelMessageEvent.class, this::onChannelMessageEvent0));
        subscriptions.add(eventManager.onEvent(GiftedMultiMonthSubCourtesyEvent.class, this::onGiftedMultiMonthSubCourtesyEvent0));
        subscriptions.add(eventManager.onEvent(SubscriptionEvent.class, this::onSubscriptionEvent0));
        subscriptions.add(eventManager.onEvent(GiftSubUpgradeEvent.class, this::onGiftSubUpgradeEvent0));
        subscriptions.add(eventManager.onEvent(PrimeSubUpgradeEvent.class, this::onPrimeSubUpgradeEvent0));
        subscriptions.add(eventManager.onEvent(ExtendSubscriptionEvent.class, this::onExtendSubscriptionEvent0));
        subscriptions.add(eventManager.onEvent(ClearChatEvent.class, this::onClearChatEvent0));
        subscriptions.add(eventManager.onEvent(DeleteMessageEvent.class, this::onDeleteMessageEvent0));
        subscriptions.add(eventManager.onEvent(EmoteOnlyEvent.class, this::onEmoteOnlyEvent0));
        subscriptions.add(eventManager.onEvent(FollowersOnlyEvent.class, this::onFollowersOnlyEvent0));
        subscriptions.add(eventManager.onEvent(RaidEvent.class, this::onRaidEvent0));
        subscriptions.add(eventManager.onEvent(Robot9000Event.class, this::onRobot9000Event0));
        subscriptions.add(eventManager.onEvent(SlowModeEvent.class, this::onSlowModeEvent0));
        subscriptions.add(eventManager.onEvent(SubscribersOnlyEvent.class, this::onSubscribersOnlyEvent0));
        subscriptions.add(eventManager.onEvent(ChannelStateEvent.class, this::onChannelStateEvent0));
        subscriptions.add(eventManager.onEvent(RaidCancellationEvent.class, this::onRaidCancellationEvent0));
        subscriptions.add(eventManager.onEvent(PrimeGiftReceivedEvent.class, this::onPrimeGiftReceivedEvent0));
        subscriptions.add(eventManager.onEvent(ChannelGoLiveEvent.class, this::onChannelGoLiveEvent0));
        subscriptions.add(eventManager.onEvent(ChannelGoOfflineEvent.class, this::onChannelGoOffLineEvent0));
    }

    public void cancel() {
        if (subscriptions == null) return;

        for (var subscription : subscriptions) {
            subscription.dispose();
        }

        subscriptions.clear();
        subscriptions = null;

        executor = null;
    }

    private boolean notMyEvent(AbstractChannelEvent channel) {
        return !channelId.equalsIgnoreCase(channel.getChannel().getId());
    }

    private boolean notMyEvent(String channelId) {
        return !this.channelId.equalsIgnoreCase(channelId);
    }

    private void onChannelMessageEvent0(ChannelMessageEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> {
            var chatData = parseMessageEvent(event.getMessageEvent());
            onChatData(chatData);
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

    protected abstract void onChatData(ChatData item);

    private void onClearChatEvent0(ClearChatEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onClearChatEvent(event));
    }

    protected abstract void onClearChatEvent(ClearChatEvent event);

    private void onDeleteMessageEvent0(DeleteMessageEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onDeleteMessageEvent(event));
    }

    protected abstract void onDeleteMessageEvent(DeleteMessageEvent event);

    private void onEmoteOnlyEvent0(EmoteOnlyEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onEmoteOnlyEvent(event));
    }

    protected abstract void onEmoteOnlyEvent(EmoteOnlyEvent event);

    private void onFollowersOnlyEvent0(FollowersOnlyEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onFollowersOnlyEvent(event));
    }

    protected abstract void onFollowersOnlyEvent(FollowersOnlyEvent event);

    private void onRobot9000Event0(Robot9000Event event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onRobot9000Event(event));
    }

    protected abstract void onRobot9000Event(Robot9000Event event);

    private void onSlowModeEvent0(SlowModeEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onSlowModeEvent(event));
    }

    protected abstract void onSlowModeEvent(SlowModeEvent event);

    private void onSubscribersOnlyEvent0(SubscribersOnlyEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onSubscribersOnlyEvent(event));
    }

    protected abstract void onSubscribersOnlyEvent(SubscribersOnlyEvent event);

    private void onChannelStateEvent0(ChannelStateEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onChannelStateEvent(event));
    }

    protected abstract void onChannelStateEvent(ChannelStateEvent event);

    private void onRaidEvent0(RaidEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onRaidEvent(event));
    }

    protected abstract void onRaidEvent(RaidEvent event);

    private void onRaidCancellationEvent0(RaidCancellationEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onRaidCancellationEvent(event));
    }

    protected abstract void onRaidCancellationEvent(RaidCancellationEvent event);

    private void onPrimeGiftReceivedEvent0(PrimeGiftReceivedEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onPrimeGiftReceivedEvent(event));
    }

    protected abstract void onPrimeGiftReceivedEvent(PrimeGiftReceivedEvent event);

    private void onChannelGoLiveEvent0(ChannelGoLiveEvent event) {
        if (notMyEvent(event.getChannel().getId())) return;
        executor.submit(() -> onChannelGoLiveEvent(event));
    }

    protected abstract void onChannelGoLiveEvent(ChannelGoLiveEvent event);

    private void onChannelGoOffLineEvent0(ChannelGoOfflineEvent event) {
        if (!notMyEvent(event.getChannel().getId())) return;
        executor.submit(() -> onChannelGoOffLineEvent(event));
    }

    protected abstract void onChannelGoOffLineEvent(ChannelGoOfflineEvent event);


    // ***** subscribe events *****

    private void onGiftedMultiMonthSubCourtesyEvent0(GiftedMultiMonthSubCourtesyEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onGiftedMultiMonthSubCourtesyEvent(event));
    }

    protected abstract void onGiftedMultiMonthSubCourtesyEvent(GiftedMultiMonthSubCourtesyEvent event);

    private void onSubscriptionEvent0(SubscriptionEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onSubscriptionEvent(event));
    }

    protected abstract void onSubscriptionEvent(SubscriptionEvent event);

    private void onGiftSubUpgradeEvent0(GiftSubUpgradeEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onGiftSubUpgradeEvent(event));
    }

    protected abstract void onGiftSubUpgradeEvent(GiftSubUpgradeEvent event);

    private void onPrimeSubUpgradeEvent0(PrimeSubUpgradeEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onPrimeSubUpgradeEvent(event));
    }

    protected abstract void onPrimeSubUpgradeEvent(PrimeSubUpgradeEvent event);

    private void onExtendSubscriptionEvent0(ExtendSubscriptionEvent event) {
        if (notMyEvent(event)) return;
        executor.submit(() -> onExtendSubscriptionEvent(event));
    }

    protected abstract void onExtendSubscriptionEvent(ExtendSubscriptionEvent event);

}
