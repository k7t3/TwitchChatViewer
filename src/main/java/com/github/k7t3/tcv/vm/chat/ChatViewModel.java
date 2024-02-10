package com.github.k7t3.tcv.vm.chat;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventListener;
import com.github.k7t3.tcv.domain.chat.ChatRoomRepository;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.vm.core.LimitedObservableList;
import com.github.k7t3.tcv.vm.service.FXTask;
import com.github.k7t3.tcv.vm.service.TaskWorker;
import com.github.twitch4j.chat.events.channel.*;
import com.github.twitch4j.chat.events.roomstate.*;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import de.saxsys.mvvmfx.SceneLifecycle;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatViewModel extends ChatRoomEventListener implements ViewModel, SceneLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatViewModel.class);

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyObjectWrapper<Image> profileImage = new ReadOnlyObjectWrapper<>();

    private final ObservableList<ChatDataViewModel> chatDataList = new LimitedObservableList<>(128);

    private final ReadOnlyBooleanWrapper chatJoined = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyBooleanWrapper live;

    private final BooleanProperty scrollToBottom = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleBadges = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleName = new SimpleBooleanProperty(true);

    private final ReadOnlyObjectWrapper<ChatRoomState> roomState = new ReadOnlyObjectWrapper<>(ChatRoomState.NORMAL);

    private final TwitchChannel channel;

    private final ChatRoomRepository repository;

    private final GlobalChatBadgeStore globalBadgeStore;

    private final ChannelChatBadgeStore badgeStore;

    private final ChannelEmoteStore emoteStore;

    private final ChatContainerViewModel containerViewModel;

    private final DefinedChatColors definedChatColors;

    ChatViewModel(
            TwitchChannel channel,
            ChatRoomRepository repository,
            GlobalChatBadgeStore globalBadgeStore,
            DefinedChatColors definedChatColors,
            ChatContainerViewModel containerViewModel
    ) {
        super(channel.getBroadcaster());
        this.channel = channel;
        live = new ReadOnlyBooleanWrapper(channel.isStreaming());
        this.repository = repository;
        this.globalBadgeStore = globalBadgeStore;
        badgeStore = new ChannelChatBadgeStore(channel);
        emoteStore = new ChannelEmoteStore();
        this.containerViewModel = containerViewModel;
        this.definedChatColors = definedChatColors;
        update();
    }

    private void update() {
        title.set(channel.getCurrentTitle());
        userName.set(channel.getBroadcasterName());
        channel.getBroadcaster().getProfileImageUrl().ifPresent(
                url -> profileImage.set(new Image(url, true)));
    }

    public FXTask<Void> joinChatAsync() {
        if (isChatJoined()) return FXTask.empty();

        var task = FXTask.task(() -> {
            channel.loadBadgesIfNotLoaded();
            repository.joinChat(this);
        });
        FXTask.setOnSucceeded(task, e -> setChatJoined(true));
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> leaveChatAsync() {
        if (!isChatJoined()) return FXTask.empty();

        var task = FXTask.task(() -> repository.leaveChat(this));
        FXTask.setOnSucceeded(task, e -> {
            // 親であるコンテナにチャットを抜けたことを伝える
            containerViewModel.left(this);
            setChatJoined(false);
        });
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    public ObservableList<ChatDataViewModel> getChatDataList() {
        return chatDataList;
    }

    @Override
    public void onViewAdded() {
    }

    @Override
    public void onViewRemoved() {
    }

    @Override
    protected void onChatData(ChatData item) {
        var viewModel = new ChatDataViewModel(item, globalBadgeStore, badgeStore, emoteStore, definedChatColors);
        Platform.runLater(() -> chatDataList.add(viewModel));
    }

    @Override
    protected void onClearChatEvent(ClearChatEvent event) {
        LOGGER.info("{} チャットクリアイベント", getUserName());
        Platform.runLater(chatDataList::clear);
    }

    @Override
    protected void onDeleteMessageEvent(DeleteMessageEvent event) {
        LOGGER.info("{} メッセージ削除イベント", getUserName());
        var msgId = event.getMsgId();
        Platform.runLater(() -> chatDataList.removeIf(item -> item.getChatData().msgId().equalsIgnoreCase(msgId)));
    }

    @Override
    protected void onEmoteOnlyEvent(EmoteOnlyEvent event) {
        if (!event.isActive()) {
            if (getRoomState() == ChatRoomState.EMOTE_ONLY) {
                LOGGER.info("{} スタンプ限定モード", getUserName());
                Platform.runLater(() -> roomState.set(ChatRoomState.NORMAL));
            }
            return;
        }
        LOGGER.info("{} スタンプ限定モード", getUserName());
        Platform.runLater(() -> roomState.set(ChatRoomState.EMOTE_ONLY));
    }

    @Override
    protected void onFollowersOnlyEvent(FollowersOnlyEvent event) {
        if (!event.isActive()) {
            if (getRoomState() == ChatRoomState.FOLLOWERS_ONLY) {
                LOGGER.info("{} フォロワー限定モード", getUserName());
                Platform.runLater(() -> roomState.set(ChatRoomState.NORMAL));
            }
            return;
        }
        LOGGER.info("{} フォロワー限定モード", getUserName());
        Platform.runLater(() -> roomState.set(ChatRoomState.FOLLOWERS_ONLY));
    }

    @Override
    protected void onRobot9000Event(Robot9000Event event) {
    }

    @Override
    protected void onSlowModeEvent(SlowModeEvent event) {
        if (!event.isActive()) {
            if (getRoomState() == ChatRoomState.SLOW_MODE) {
                LOGGER.info("{} スローモード", getUserName());
                Platform.runLater(() -> roomState.set(ChatRoomState.NORMAL));
            }
            return;
        }
        LOGGER.info("{} スローモード", getUserName());
        Platform.runLater(() -> roomState.set(ChatRoomState.SLOW_MODE));
    }

    @Override
    protected void onSubscribersOnlyEvent(SubscribersOnlyEvent event) {
        if (!event.isActive()) {
            if (getRoomState() == ChatRoomState.SUBSCRIBERS_ONLY) {
                LOGGER.info("{} サブスクライバー限定モード", getUserName());
                Platform.runLater(() -> roomState.set(ChatRoomState.NORMAL));
            }
            return;
        }
        LOGGER.info("{} サブスクライバー限定モード", getUserName());
        Platform.runLater(() -> roomState.set(ChatRoomState.SUBSCRIBERS_ONLY));
    }

    @Override
    protected void onChannelStateEvent(ChannelStateEvent event) {
    }

    @Override
    protected void onRaidEvent(RaidEvent event) {
    }

    @Override
    protected void onRaidCancellationEvent(RaidCancellationEvent event) {
    }

    @Override
    protected void onPrimeGiftReceivedEvent(PrimeGiftReceivedEvent event) {
    }

    @Override
    protected void onChannelGoLiveEvent(ChannelGoLiveEvent event) {
    }

    @Override
    protected void onChannelGoOffLineEvent(ChannelGoOfflineEvent event) {
    }

    @Override
    protected void onGiftedMultiMonthSubCourtesyEvent(GiftedMultiMonthSubCourtesyEvent event) {
    }

    @Override
    protected void onSubscriptionEvent(SubscriptionEvent event) {
    }

    @Override
    protected void onGiftSubUpgradeEvent(GiftSubUpgradeEvent event) {
    }

    @Override
    protected void onPrimeSubUpgradeEvent(PrimeSubUpgradeEvent event) {
    }

    @Override
    protected void onExtendSubscriptionEvent(ExtendSubscriptionEvent event) {
    }

    // ********** PROPERTIES **********

    private ReadOnlyStringWrapper titleWrapper() { return title; }
    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }
    public void setTitle(String title) { this.title.set(title); }

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }
    public void setUserName(String userName) { this.userName.set(userName); }

    private ReadOnlyObjectWrapper<Image> profileImageWrapper() { return profileImage; }
    public ReadOnlyObjectProperty<Image> profileImageProperty() { return profileImage.getReadOnlyProperty(); }
    public Image getProfileImage() { return profileImage.get(); }
    private void setProfileImage(Image profileImage) { this.profileImage.set(profileImage); }

    private ReadOnlyBooleanWrapper chatJoinedWrapper() { return chatJoined; }
    public ReadOnlyBooleanProperty chatJoinedProperty() { return chatJoined.getReadOnlyProperty(); }
    public boolean isChatJoined() { return chatJoined.get(); }
    public void setChatJoined(boolean chatJoined) { this.chatJoined.set(chatJoined); }

    public BooleanProperty scrollToBottomProperty() { return scrollToBottom; }
    public boolean isScrollToBottom() { return scrollToBottom.get(); }
    public void setScrollToBottom(boolean scrollToBottom) { this.scrollToBottom.set(scrollToBottom); }

    private ReadOnlyBooleanWrapper liveWrapper() { return live; }
    public ReadOnlyBooleanProperty liveProperty() { return live.getReadOnlyProperty(); }
    public boolean isLive() { return live.get(); }
    private void setLive(boolean live) { this.live.set(live); }

    public BooleanProperty visibleBadgesProperty() { return visibleBadges; }
    public boolean isVisibleBadges() { return visibleBadges.get(); }
    public void setVisibleBadges(boolean visibleBadges) { this.visibleBadges.set(visibleBadges); }

    public BooleanProperty visibleNameProperty() { return visibleName; }
    public boolean isVisibleName() { return visibleName.get(); }
    public void setVisibleName(boolean visibleName) { this.visibleName.set(visibleName); }

    public ReadOnlyObjectProperty<ChatRoomState> roomStateProperty() { return roomState.getReadOnlyProperty(); }
    public ChatRoomState getRoomState() { return roomState.get(); }
}
