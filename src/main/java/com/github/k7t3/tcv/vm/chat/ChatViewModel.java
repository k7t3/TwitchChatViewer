package com.github.k7t3.tcv.vm.chat;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoomRepository;
import com.github.k7t3.tcv.vm.core.FXSubscriber;
import com.github.k7t3.tcv.vm.service.FXTask;
import com.github.k7t3.tcv.vm.service.TaskWorker;
import de.saxsys.mvvmfx.SceneLifecycle;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ChatViewModel extends FXSubscriber<ChatData> implements ViewModel, SceneLifecycle {

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ObservableList<ChatDataViewModel> chatDataList = FXCollections.observableArrayList();

    private final ReadOnlyBooleanWrapper chatJoined = new ReadOnlyBooleanWrapper(false);

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
        this.channel = channel;
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
    }

    public FXTask<Void> joinChannelAsync() {
        if (isChatJoined()) return FXTask.empty();

        var task = FXTask.task(() -> {
            channel.loadBadgesIfNotLoaded();
            repository.joinChat(channel.getBroadcaster());
            repository.getChatPublisher().subscribe(this);
        });
        FXTask.setOnSucceeded(task, e -> setChatJoined(true));
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> leaveChannelAsync() {
        if (!isChatJoined()) return FXTask.empty();

        var task = FXTask.task(() -> {
            repository.leaveChat(channel.getBroadcaster());

            // チャットのサブスクリプションを破棄
            dispose();
        });
        FXTask.setOnSucceeded(task, e -> {
            // 親であるコンテナにチャットを抜けたことを伝える
            containerViewModel.left(this);
            setChatJoined(false);
        });
        TaskWorker.getInstance().submit(task);
        return task;
    }

    @Override
    protected void handleOnBackground(ChatData item) {
        // 違うチャンネルのメッセージは無視する
        if (!channel.getBroadcasterId().equalsIgnoreCase(item.channelId())) {
            return;
        }

        var viewModel = new ChatDataViewModel(item, globalBadgeStore, badgeStore, emoteStore, definedChatColors);
        Platform.runLater(() -> chatDataList.add(viewModel));
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

    // ********** PROPERTIES **********

    private ReadOnlyStringWrapper titleWrapper() { return title; }
    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }
    public void setTitle(String title) { this.title.set(title); }

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }
    public void setUserName(String userName) { this.userName.set(userName); }

    private ReadOnlyBooleanWrapper chatJoinedWrapper() { return chatJoined; }
    public ReadOnlyBooleanProperty chatJoinedProperty() { return chatJoined.getReadOnlyProperty(); }
    public boolean isChatJoined() { return chatJoined.get(); }
    public void setChatJoined(boolean chatJoined) { this.chatJoined.set(chatJoined); }
}
