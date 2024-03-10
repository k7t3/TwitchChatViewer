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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;

public class SingleChatRoomViewModel extends ChatRoomViewModel implements ViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleChatRoomViewModel.class);
    
    private final ReadOnlyObjectWrapper<TwitchChannelViewModel> channel;

    private final ObservableSet<ChatRoomState> roomStates = FXCollections.observableSet(new HashSet<>());

    SingleChatRoomViewModel(
            ChatRoomContainerViewModel containerViewModel,
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            TwitchChannel channel
    ) {
        super(globalChatBadgeStore, emoteStore, definedChatColors, containerViewModel);
        this.channel = new ReadOnlyObjectWrapper<>(new TwitchChannelViewModel(channel));

        var channelViewModel = getChannel();
        channelViewModel.getChannelListeners().add(this);
        channelViewModel.getChatRoomListeners().add(this);
    }

    @Override
    public FXTask<?> joinChatAsync() {
        var channel = getChannel();
        return channel.joinChatAsync();
    }

    @Override
    public FXTask<Void> leaveChatAsync() {
        var channel = getChannel();

        if (channel.isChatJoined()) {
            containerViewModel.onLeft(this);
        }

        return channel.leaveChatAsync();
    }

    @Override
    boolean hasChannel(TwitchChannel channel) {
        var viewModel = getChannel();
        return Objects.equals(viewModel.getChannel(), channel);
    }

    @Override
    protected TwitchChannelViewModel getChannel(TwitchChannel channel) {
        var viewModel = getChannel();

        if (!Objects.equals(viewModel.getChannel(), channel))
            throw new IllegalArgumentException();

        return viewModel;
    }

    public ObservableSet<ChatRoomState> getRoomStates() {
        return roomStates;
    }

    @Override
    public void onClipPosted(ChatRoom chatRoom, VideoClip clip) {
        // no-op
    }

    @Override
    public void onStateUpdated(ChatRoom chatRoom, ChatRoomState roomState, boolean active) {
        LOGGER.info("{} room state updated {}", getChannel().getUserLogin(), roomState);
        Platform.runLater(() -> {
            if (active)
                roomStates.add(roomState);
            else
                roomStates.remove(roomState);
        });
    }

    @Override
    public void onOnline(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> getChannel().updateStreamInfo(info));
    }

    @Override
    public void onOffline(TwitchChannel channel) {
        Platform.runLater(() -> getChannel().updateStreamInfo(null));
    }

    @Override
    public void onViewerCountUpdated(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> getChannel().updateStreamInfo(info));
    }

    @Override
    public void onTitleChanged(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> getChannel().updateStreamInfo(info));
    }

    @Override
    public void onGameChanged(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> getChannel().updateStreamInfo(info));
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<TwitchChannelViewModel> channelProperty() { return channel.getReadOnlyProperty(); }
    public TwitchChannelViewModel getChannel() { return channel.get(); }
    
}
