package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.chat.ChannelChatBadgeStore;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.scene.image.Image;

import java.util.HashSet;

public class TwitchChannelViewModel {

    private final ReadOnlyObjectWrapper<Broadcaster> broadcaster;

    private ReadOnlyObjectWrapper<Image> profileImage;

    private ReadOnlyObjectWrapper<Image> offlineImage;

    private ReadOnlyObjectWrapper<StreamInfo> streamInfo;

    private ReadOnlyBooleanWrapper live;

    private ReadOnlyBooleanWrapper chatJoined;

    private ObservableSet<TwitchChannelListener> channelListeners;
    private ObservableSet<ChatRoomListener> chatRoomListeners;

    private final ChannelChatBadgeStore chatBadgeStore;

    private final TwitchChannel channel;

    private ChatRoom chatRoom;

    private final ChannelViewModelRepository repository;

    public TwitchChannelViewModel(TwitchChannel channel, ChannelViewModelRepository repository) {
        this.channel = channel;
        this.repository = repository;
        broadcaster = new ReadOnlyObjectWrapper<>(channel.getBroadcaster());
        chatBadgeStore = new ChannelChatBadgeStore(channel);

        if (channel.isStreaming()) {
            updateStreamInfo(channel.getStream());
        }
    }

    public void updateStreamInfo(StreamInfo streamInfo) {
        streamInfoWrapper().set(streamInfo);
        liveWrapper().set(streamInfo != null);
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    public ObservableSet<TwitchChannelListener> getChannelListeners() {
        if (channelListeners == null) {
            channelListeners = FXCollections.observableSet(new HashSet<>());
            channelListeners.addListener(this::channelListenerChanged);
        }
        return channelListeners;
    }

    private void channelListenerChanged(SetChangeListener.Change<? extends TwitchChannelListener> c) {
        if (!isChatJoined()) return;

        if (c.wasAdded()) {
            channel.addListener(c.getElementAdded());
        }
        if (c.wasRemoved()) {
            channel.removeListener(c.getElementRemoved());
        }
    }

    public ObservableSet<ChatRoomListener> getChatRoomListeners() {
        if (chatRoomListeners == null) {
            chatRoomListeners = FXCollections.observableSet(new HashSet<>());
            chatRoomListeners.addListener(this::chatRoomListenerChanged);
        }
        return chatRoomListeners;
    }

    private void chatRoomListenerChanged(SetChangeListener.Change<? extends ChatRoomListener> c) {
        if (!isChatJoined()) return;

        if (c.wasAdded()) {
            chatRoom.addListener(c.getElementAdded());
        }
        if (c.wasRemoved()) {
            chatRoom.removeListener(c.getElementRemoved());
        }
    }

    public FXTask<ChatRoom> joinChatAsync() {
        if (isChatJoined()) return FXTask.of(channel.getOrJoinChatRoom());

        var task = FXTask.task(() -> {
            channel.loadBadgesIfNotLoaded();
            return channel.getOrJoinChatRoom();
        });
        FXTask.setOnSucceeded(task, e -> {
            chatRoom = task.getValue();

            if (chatRoomListeners != null) {
                chatRoomListeners.forEach(chatRoom::addListener);
            }

            if (channelListeners != null) {
                channelListeners.forEach(channel::addListener);
            }

            chatJoinedWrapper().set(true);
        });
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> leaveChatAsync() {
        if (!isChatJoined()) return FXTask.empty();

        if (chatJoined != null) {
            chatJoined.set(false);
        }

        var task = FXTask.task(() -> {
            // leave
            channel.leaveChat();

            repository.releaseChannel(this);

            if (chatRoomListeners != null) {
                chatRoomListeners.forEach(chatRoom::removeListener);
            }

            if (channelListeners != null) {
                channelListeners.forEach(channel::removeListener);
            }
        });
        task.setSucceeded(() -> chatRoom = null);
        task.runAsync();

        return task;
    }

    public ChannelChatBadgeStore getChatBadgeStore() {
        return chatBadgeStore;
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<Broadcaster> broadcasterProperty() { return broadcaster.getReadOnlyProperty(); }
    public Broadcaster getBroadcaster() { return broadcaster.get(); }

    public ObservableValue<String> observableUserLogin() { return broadcaster.map(Broadcaster::getUserLogin); }
    public String getUserLogin() { return broadcaster.get().getUserLogin(); }

    public ObservableValue<String> observableUserName() { return broadcaster.map(Broadcaster::getUserName); }
    public String getUserName() { return broadcaster.get().getUserName(); }

    public ObservableValue<String> observableTitle() { return streamInfoWrapper().map(StreamInfo::title).orElse(""); }
    public String getTitle() { return streamInfo == null ? "" : observableTitle().getValue(); }

    public ObservableValue<Integer> observableViewerCount() { return streamInfoWrapper().map(StreamInfo::viewerCount).orElse(-1); }
    public int getViewerCount() { return streamInfo == null ? -1 : observableViewerCount().getValue(); }

    public ObservableValue<String> observableGameName() { return streamInfoWrapper().map(StreamInfo::gameName).orElse(""); }
    public String getGameName() { return streamInfo == null ? "" : observableGameName().getValue(); }

    private ReadOnlyObjectWrapper<Image> profileImageWrapper() {
        if (profileImage == null) {
            profileImage = new ReadOnlyObjectWrapper<>(new Image(
                    channel.getBroadcaster().getProfileImageUrl(),
                    64,
                    64,
                    true,
                    true,
                    true
            ));
        }
        return profileImage;
    }
    public ReadOnlyObjectProperty<Image> profileImageProperty() { return profileImageWrapper().getReadOnlyProperty(); }
    public Image getProfileImage() { return profileImageWrapper().get(); }

    private ReadOnlyObjectWrapper<Image> offlineImageWrapper() {
        if (offlineImage == null) {
            offlineImage = new ReadOnlyObjectWrapper<>();
            channel.getBroadcaster().getOfflineImageUrl().ifPresent(url -> offlineImage.set(new Image(url, true)));
        }
        return offlineImage;
    }
    public ReadOnlyObjectProperty<Image> offlineImageProperty() { return offlineImageWrapper().getReadOnlyProperty(); }
    public Image getOfflineImage() { return offlineImageWrapper().get(); }

    private ReadOnlyObjectWrapper<StreamInfo> streamInfoWrapper() {
        if (streamInfo == null) {
            streamInfo = new ReadOnlyObjectWrapper<>();
        }
        return streamInfo;
    }
    public ReadOnlyObjectProperty<StreamInfo> streamInfoProperty() { return streamInfoWrapper().getReadOnlyProperty(); }
    public StreamInfo getStreamInfo() { return streamInfoWrapper().get(); }

    private ReadOnlyBooleanWrapper liveWrapper() {
        if (live == null) {
            live = new ReadOnlyBooleanWrapper();
        }
        return live;
    }
    public ReadOnlyBooleanProperty liveProperty() { return liveWrapper().getReadOnlyProperty(); }
    public boolean isLive() { return live != null && live.get(); }

    private ReadOnlyBooleanWrapper chatJoinedWrapper() {
        if (chatJoined == null) {
            chatJoined = new ReadOnlyBooleanWrapper();
        }
        return chatJoined;
    }
    public ReadOnlyBooleanProperty chatJoinedProperty() { return chatJoinedWrapper().getReadOnlyProperty(); }
    public boolean isChatJoined() { return chatJoined != null && chatJoined.get(); }

}
