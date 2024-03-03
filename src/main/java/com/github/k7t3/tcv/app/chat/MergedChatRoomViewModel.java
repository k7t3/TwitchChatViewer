package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

public class MergedChatRoomViewModel extends ChatRoomViewModelBase implements ViewModel, TwitchChannelListener {

    private static final int DEFAULT_ITEM_COUNT_LIMIT = 512;

    private final ObservableList<TwitchChannelViewModel> channels = FXCollections.observableArrayList();

    private final ChatRoomContainerViewModel containerViewModel;

    MergedChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            List<ChatRoomViewModel> chatRooms,
            ChatRoomContainerViewModel containerViewModel
    ) {
        super(globalChatBadgeStore, emoteStore, definedChatColors);
        setItemCountLimit(DEFAULT_ITEM_COUNT_LIMIT);
        this.containerViewModel = containerViewModel;

        initChatRooms(chatRooms);
    }

    private void initChatRooms(List<ChatRoomViewModel> chatRooms) {
        for (var chatRoom : chatRooms) {
            var channel = chatRoom.getChannel();
            channel.getChannelListeners().add(this);
            channel.getChatRoomListeners().add(this);

            // 既存のViewModelによる監視を解除
            channel.getChannelListeners().remove(chatRoom);
            channel.getChatRoomListeners().remove(chatRoom);

            channels.add(channel);
        }
    }

    public ObservableList<TwitchChannelViewModel> getChannels() {
        return channels;
    }

    @Override
    boolean hasChannel(TwitchChannel channel) {
        for (var channelViewModel : channels) {
            if (channelViewModel.getChannel().equals(channel))
                return true;
        }
        return false;
    }

    @Override
    protected TwitchChannelViewModel getChannel(TwitchChannel channel) {
        for (var channelViewModel : channels) {
            if (channelViewModel.getChannel().equals(channel))
                return channelViewModel;
        }
        throw new IllegalArgumentException("Unexpected channel: " + channel.toString());
    }

    @Override
    public FXTask<?> joinChatAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FXTask<?> leaveChatAsync() {

        containerViewModel.onLeft(this);

        // TODO 複数タスクの集約・同期
        for (var channel : channels) {
            channel.leaveChatAsync();
        }

        return FXTask.of(channels.size());
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, VideoClip clip) {
        // no-op
    }

    @Override
    public void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active) {
        // no-op
    }

    private void updateStreamInfo(TwitchChannel channel, StreamInfo streamInfo) {
        Platform.runLater(() -> {
            for (var channelViewModel : channels) {
                if (channelViewModel.getChannel().equals(channel))
                    channelViewModel.updateStreamInfo(streamInfo);
            }
        });
    }

    @Override
    public void onOnline(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }

    @Override
    public void onOffline(TwitchChannel channel) {
        updateStreamInfo(channel, null);
    }

    @Override
    public void onViewerCountUpdated(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }

    @Override
    public void onTitleChanged(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }

    @Override
    public void onGameChanged(TwitchChannel channel, StreamInfo info) {
        updateStreamInfo(channel, info);
    }
}
