/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.emoji.ChatEmojiStore;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomStateUpdatedEvent;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
            ChatEmojiStore emojiCache,
            TwitchChannelViewModel channel,
            ChatFilters chatFilters
    ) {
        super(globalChatBadgeStore, emoteStore, definedChatColors, emojiCache, containerViewModel, chatFilters);
        this.channel = new ReadOnlyObjectWrapper<>(channel);
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
    public boolean accept(TwitchChannel channel) {
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

    @Override
    public String getIdentity() {
        var login = channel.get().getUserLogin();
        return Base64.getEncoder().encodeToString(login.getBytes(StandardCharsets.UTF_8));
    }

    public ObservableSet<ChatRoomState> getRoomStates() {
        return roomStates;
    }

    @Override
    public void onStateUpdated(ChatRoomStateUpdatedEvent e) {
        var roomState = e.getState();
        var active = e.isActive();

        LOGGER.info("{} room state updated {}", getChannel().getUserLogin(), roomState);
        Platform.runLater(() -> {
            if (active)
                roomStates.add(roomState);
            else
                roomStates.remove(roomState);
        });
    }

    // ******************** PROPERTIES ********************

    public ReadOnlyObjectProperty<TwitchChannelViewModel> channelProperty() { return channel.getReadOnlyProperty(); }
    public TwitchChannelViewModel getChannel() { return channel.get(); }
    
}
