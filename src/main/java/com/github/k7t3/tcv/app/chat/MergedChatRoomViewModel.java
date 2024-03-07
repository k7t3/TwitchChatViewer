package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.List;
import java.util.Map;

public class MergedChatRoomViewModel extends ChatRoomViewModelBase implements ViewModel {

    private static final int DEFAULT_ITEM_COUNT_LIMIT = 512;

    private final ObservableMap<TwitchChannelViewModel, ChatRoomViewModel> channels = FXCollections.observableHashMap();

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
            addChatRoom(chatRoom);
        }
    }

    public void addChatRoom(ChatRoomViewModel chatRoom) {
        var channel = chatRoom.getChannel();

        if (channel.isLive()) {
            channel.getChatRoomListeners().remove(chatRoom);
            channel.getChannelListeners().remove(chatRoom);
        } else {
            throw new IllegalStateException();
        }

        channel.getChatRoomListeners().add(this);
        channel.getChannelListeners().add(this);

        channels.put(channel, chatRoom);
    }

    private void onChannelRemoved() {
        if (channels.size() != 1) {
            return;
        }

        var chatRoom = channels.entrySet().stream().findFirst().orElseThrow().getValue();
        separateChatRoom(chatRoom);

        containerViewModel.getChatList().remove(this);
    }

    public void separateChatRoom(ChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channel.getChatRoomListeners().remove(this);
        channel.getChannelListeners().remove(this);

        channels.remove(channel);

        channel.getChannelListeners().add(chatRoom);
        channel.getChatRoomListeners().add(chatRoom);

        containerViewModel.getChatList().add(chatRoom);

        onChannelRemoved();
    }

    public void closeChatRoom(ChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channel.getChatRoomListeners().remove(this);
        channel.getChannelListeners().remove(this);

        channels.remove(channel);

        onChannelRemoved();
    }

    public MergedChatRoomViewModel aggregate(MergedChatRoomViewModel mergedChatRoom) {
        if (this == mergedChatRoom) return this;

        for (var entry : mergedChatRoom.channels.entrySet()) {
            var channel = entry.getKey();
            var chatRoom = entry.getValue();

            if (!channels.containsValue(chatRoom)) continue;

            channel.getChatRoomListeners().remove(mergedChatRoom);
            channel.getChannelListeners().remove(mergedChatRoom);

            channel.getChatRoomListeners().add(this);
            channel.getChannelListeners().add(this);

            channels.put(channel, chatRoom);
            containerViewModel.getChatList().remove(mergedChatRoom);
        }

        return this;
    }

    public ObservableMap<TwitchChannelViewModel, ChatRoomViewModel> getChannels() {
        return channels;
    }

    @Override
    boolean hasChannel(TwitchChannel channel) {
        for (var channelViewModel : channels.keySet()) {
            if (channelViewModel.getChannel().equals(channel))
                return true;
        }
        return false;
    }

    @Override
    protected TwitchChannelViewModel getChannel(TwitchChannel channel) {
        for (var channelViewModel : channels.keySet()) {
            if (channelViewModel.getChannel().equals(channel))
                return channelViewModel;
        }
        throw new IllegalArgumentException("Unexpected channel: " + channel.toString());
    }

    @Override
    public FXTask<?> joinChatAsync() {
        // このインスタンスはチャットへ参加済みのチャンネルしか扱わないため
        throw new UnsupportedOperationException();
    }

    @Override
    public FXTask<?> leaveChatAsync() {

        containerViewModel.onLeft(this);

        // TODO 複数タスクの集約・同期
        for (var channel : channels.keySet()) {
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
        // ConcurrentModificationExceptionが内部で起こり得る？
        channels.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getChannel().isChatJoined() && entry.getKey().getChannel().getChatRoom().equals(chatRoom))
                .findFirst()
                .map(Map.Entry::getValue)
                .ifPresent(chatRoomViewModel -> chatRoomViewModel.onStateUpdated(chatRoom, roomState, active));
    }

    private void updateStreamInfo(TwitchChannel channel, StreamInfo streamInfo) {
        Platform.runLater(() -> {
            for (var channelViewModel : channels.keySet()) {
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
