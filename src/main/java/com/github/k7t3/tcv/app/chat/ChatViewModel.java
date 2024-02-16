package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;
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
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatViewModel implements ViewModel, ChatRoomListener {

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

    private final GlobalChatBadgeStore globalBadgeStore;

    private final ChannelChatBadgeStore badgeStore;

    private final ChannelEmoteStore emoteStore;

    private final ChatContainerViewModel containerViewModel;

    private final DefinedChatColors definedChatColors;

    private ChatRoom chatRoom;

    ChatViewModel(
            TwitchChannel channel,
            GlobalChatBadgeStore globalBadgeStore,
            DefinedChatColors definedChatColors,
            ChatContainerViewModel containerViewModel
    ) {
        this.channel = channel;
        live = new ReadOnlyBooleanWrapper(channel.isStreaming());
        this.globalBadgeStore = globalBadgeStore;
        badgeStore = new ChannelChatBadgeStore(channel);
        emoteStore = new ChannelEmoteStore();
        this.containerViewModel = containerViewModel;
        this.definedChatColors = definedChatColors;
        update();
    }

    private void update() {
        title.set(channel.getStream().title());
        userName.set(channel.getBroadcaster().getUserName());
        channel.getBroadcaster().getProfileImageUrl().ifPresent(
                url -> profileImage.set(new Image(url, true))
        );
    }

    public FXTask<?> joinChatAsync() {
        if (isChatJoined()) return FXTask.empty();

        var task = FXTask.task(() -> {
            channel.loadBadgesIfNotLoaded();
            return channel.getChatRoom();
        });
        FXTask.setOnSucceeded(task, e -> {
            chatRoom = task.getValue();
            chatRoom.addListener(this);
            setChatJoined(true);
        });
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> leaveChatAsync() {
        if (!isChatJoined()) return FXTask.empty();

        chatRoom = null;

        // 親であるコンテナにチャットを抜けたことを伝える
        containerViewModel.onLeft(this);

        setChatJoined(false);

        var task = FXTask.task(channel::leaveChat);
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
    public void onStateUpdated(ChatRoomState roomState) {
        LOGGER.info("{} room state updated {}", getUserName(), roomState);
        Platform.runLater(() -> this.roomState.set(roomState));
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
