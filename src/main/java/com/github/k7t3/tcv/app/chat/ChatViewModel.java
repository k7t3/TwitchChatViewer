package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import com.github.k7t3.tcv.domain.channel.VideoClip;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.app.core.LimitedObservableList;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class ChatViewModel implements ViewModel, TwitchChannelListener, ChatRoomListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatViewModel.class);

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper gameName = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyIntegerWrapper viewerCount = new ReadOnlyIntegerWrapper();

    private final ReadOnlyObjectWrapper<LocalDateTime> startedAt = new ReadOnlyObjectWrapper<>();

    private final ReadOnlyObjectWrapper<Image> profileImage = new ReadOnlyObjectWrapper<>();

    private final ObservableList<ChatDataViewModel> chatDataList = new LimitedObservableList<>(128);

    private final ReadOnlyBooleanWrapper chatJoined = new ReadOnlyBooleanWrapper(false);

    private final ReadOnlyBooleanWrapper live;

    private final BooleanProperty scrollToBottom = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleBadges = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleName = new SimpleBooleanProperty(true);

    private final ObservableList<ChatRoomState> roomStates = FXCollections.observableArrayList();

    private final TwitchChannel channel;

    private final GlobalChatBadgeStore globalBadgeStore;

    private final ChannelChatBadgeStore badgeStore;

    private final ChannelEmoteStore emoteStore;

    private final ChatContainerViewModel containerViewModel;

    private final DefinedChatColors definedChatColors;

    private ChatRoom chatRoom;

    private final List<ChatRoomListener> defaultChatRoomListeners;

    ChatViewModel(
            TwitchChannel channel,
            GlobalChatBadgeStore globalBadgeStore,
            DefinedChatColors definedChatColors,
            ChatContainerViewModel containerViewModel,
            List<ChatRoomListener> defaultChatRoomListeners
    ) {
        this.channel = channel;
        live = new ReadOnlyBooleanWrapper(channel.isStreaming());
        this.globalBadgeStore = globalBadgeStore;
        badgeStore = new ChannelChatBadgeStore(channel);
        emoteStore = new ChannelEmoteStore();
        this.containerViewModel = containerViewModel;
        this.definedChatColors = definedChatColors;
        this.defaultChatRoomListeners = defaultChatRoomListeners;
        update();
    }

    private void update() {
        if (channel.isStreaming()) {
            LOGGER.info("{} {}", channel.getBroadcaster().getUserName(), channel.getStream().title());
            updateStreamInfo(channel.getStream());
        }
        userName.set(channel.getBroadcaster().getUserName());
        channel.getBroadcaster().getProfileImageUrl().ifPresent(
                url -> profileImage.set(new Image(url, true))
        );
    }

    private void updateStreamInfo(StreamInfo info) {
        title.set(info.title());
        gameName.set(info.gameName());
        viewerCount.set(info.viewerCount());
        startedAt.set(info.startedAt());
        live.set(true);
    }

    public FXTask<?> joinChatAsync() {
        if (isChatJoined()) return FXTask.empty();

        var task = FXTask.task(() -> {
            channel.loadBadgesIfNotLoaded();

            var chatRoom = channel.getChatRoom();

            // チャットルームに関するイベントリスナを追加
            chatRoom.addListener(this);

            for (var listener : defaultChatRoomListeners)
                chatRoom.addListener(listener);

            // チャンネルに関するイベントリスナを追加
            channel.addListener(this);

            return chatRoom;
        });
        FXTask.setOnSucceeded(task, e -> {
            chatRoom = task.getValue();

            // チャットルームへの参加に成功
            setChatJoined(true);
        });
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> leaveChatAsync() {
        if (!isChatJoined()) return FXTask.empty();

        // 親であるコンテナにチャットを抜けたことを伝える
        containerViewModel.onLeft(this);

        setChatJoined(false);

        var task = FXTask.task(() -> {
            channel.leaveChat();

            // チャットルームに関するリスナを削除
            chatRoom.removeListener(this);

            for (var listener : defaultChatRoomListeners)
                chatRoom.removeListener(listener);

            // チャンネルに関するリスナを削除
            channel.removeListener(this);
        });
        FXTask.setOnSucceeded(task, e -> chatRoom = null);
        TaskWorker.getInstance().submit(task);

        return task;
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    public ObservableList<ChatDataViewModel> getChatDataList() {
        return chatDataList;
    }

    public ObservableList<ChatRoomState> getRoomStates() {
        return roomStates;
    }

    @Override
    public void onChatDataPosted(ChatData item) {
        var chatData = new ChatDataViewModel(
                item,
                globalBadgeStore,
                badgeStore,
                emoteStore,
                definedChatColors
        );
        Platform.runLater(() -> chatDataList.add(chatData));
    }

    @Override
    public void onClipPosted(VideoClip clip) {
        LOGGER.info("{} clip found {}", getUserName(), clip);
    }

    @Override
    public void onChatCleared() {
        LOGGER.info("{} chat cleared", getUserName());
        Platform.runLater(chatDataList::clear);
    }

    @Override
    public void onChatMessageDeleted(String messageId) {
        LOGGER.info("chat message deleted");
        Platform.runLater(() -> {

            for (var data : chatDataList) {
                if (data.getChatData().msgId().equalsIgnoreCase(messageId)) {
                    data.setDeleted(true);
                    break;
                }
            }
        });
    }

    @Override
    public void onStateUpdated(ChatRoomState roomState, boolean active) {
        LOGGER.info("{} room state updated {}", getUserName(), roomState);
        Platform.runLater(() -> {
            if (active)
                roomStates.add(roomState);
            else
                roomStates.remove(roomState);
        });
    }

    @Override
    public void onRaidReceived(String raiderName, int viewerCount) {
        LOGGER.info("{} raid received raider={}, viewerCount={}", getUserName(), raiderName, viewerCount);
    }

    @Override
    public void onUserSubscribed(String userName) {
        LOGGER.info("{} subscribed by {}", getUserName(), userName);
    }

    @Override
    public void onUserGiftedSubscribe(String giverName, String userName) {
        LOGGER.info("{} {} sub gifted by {}", getUserName(), userName, giverName);
    }

    @Override
    public void onOnline(StreamInfo info) {
        Platform.runLater(() -> updateStreamInfo(info));
    }

    @Override
    public void onOffline() {
        Platform.runLater(() -> live.set(false));
    }

    @Override
    public void onViewerCountUpdated(StreamInfo info) {
        Platform.runLater(() -> updateStreamInfo(info));
    }

    @Override
    public void onTitleChanged(StreamInfo info) {
        Platform.runLater(() -> updateStreamInfo(info));
    }

    @Override
    public void onGameChanged(StreamInfo info) {
        Platform.runLater(() -> updateStreamInfo(info));
    }

    // ********** PROPERTIES **********

    private ReadOnlyStringWrapper titleWrapper() { return title; }
    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }
    public void setTitle(String title) { this.title.set(title); }

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }
    private void setUserName(String userName) { this.userName.set(userName); }

    public ReadOnlyStringProperty gameNameProperty() { return gameName.getReadOnlyProperty(); }
    public String getGameName() { return gameName.get(); }

    public ReadOnlyIntegerProperty viewerCountProperty() { return viewerCount.getReadOnlyProperty(); }
    public int getViewerCount() { return viewerCount.get(); }

    public ReadOnlyObjectProperty<LocalDateTime> startedAtProperty() { return startedAt.getReadOnlyProperty(); }
    public LocalDateTime getStartedAt() { return startedAt.get(); }

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

}
