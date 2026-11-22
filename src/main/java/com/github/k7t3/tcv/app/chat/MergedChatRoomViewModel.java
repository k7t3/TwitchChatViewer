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
import com.github.k7t3.tcv.app.service.FXTaskGroup;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomStateUpdatedEvent;
import de.saxsys.mvvmfx.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class MergedChatRoomViewModel extends ChatRoomViewModel implements ViewModel {

    private static final int DEFAULT_ITEM_COUNT_LIMIT = 512;

    private final ObservableMap<TwitchChannelViewModel, SingleChatRoomViewModel> channels = FXCollections.observableHashMap();

    MergedChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            List<SingleChatRoomViewModel> chatRooms,
            ChatEmojiStore emojiCache,
            ChatRoomContainerViewModel containerViewModel,
            ChatFilters chatFilters
    ) {
        this(globalChatBadgeStore, emoteStore, definedChatColors, chatRooms, emojiCache, containerViewModel,
                chatFilters, DEFAULT_ITEM_COUNT_LIMIT);
    }

    MergedChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            List<SingleChatRoomViewModel> chatRooms,
            ChatEmojiStore emojiCache,
            ChatRoomContainerViewModel containerViewModel,
            ChatFilters chatFilters,
            int initialLimit
    ) {
        super(globalChatBadgeStore, emoteStore, definedChatColors, emojiCache, containerViewModel, chatFilters);
        setChatCacheSize(initialLimit);

        initChatRooms(chatRooms);
    }

    private void initChatRooms(List<SingleChatRoomViewModel> chatRooms) {
        for (var chatRoom : chatRooms) {
            addChatRoom(chatRoom);
        }
    }

    @Override
    public String getIdentity() {
        var joined = channels.keySet().stream()
                .map(TwitchChannelViewModel::getUserLogin)
                .sorted()
                .collect(Collectors.joining(","));
        return Base64.getEncoder().encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    public void addChatRoom(SingleChatRoomViewModel chatRoom) {
        var channel = chatRoom.getChannel();
        mergeHistory(chatRoom.getChatDataList());
        channels.put(channel, chatRoom);
    }

    /**
     * チャンネルが削除されたとき、残りをチャンネル数を確認して
     * 最後の一つであれば個別のチャンネルとして切り離し、このインスタンスは取り除く。
     */
    private void onChannelRemoved() {
        if (channels.size() != 1) {
            return;
        }

        var chatRoom = channels.entrySet().stream().findFirst().orElseThrow().getValue();
        separateChatRoom(chatRoom);

        containerViewModel.getChatRoomList().remove(this);
    }

    public void separateAll() {
        var channels = new ArrayList<>(this.channels.values());
        channels.forEach(this::separateChatRoom);
    }

    public void separateChatRoom(SingleChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channels.remove(channel);

        // 分離するチャンネルのチャットを取り出す
        var chatList = getChatDataList().stream()
                .filter(c -> c.getChannel().equals(channel))
                .toList();

        // 分離したチャンネルにチャット情報を渡す
        chatRoom.replaceChatHistory(chatList);

        // 現在のチャットから削除
        getChatDataList().removeAll(chatList);

        containerViewModel.getChatRoomList().add(chatRoom);

        onChannelRemoved();
    }

    public void closeChatRoom(SingleChatRoomViewModel chatRoom) {
        if (!channels.containsValue(chatRoom)) return;

        var channel = chatRoom.getChannel();

        channels.remove(channel);

        chatRoom.leaveChatAsync();
        onChannelRemoved();
    }

    public void aggregate(MergedChatRoomViewModel mergedChatRoom) {
        if (this == mergedChatRoom) return;
        var incoming = new HashMap<TwitchChannelViewModel, SingleChatRoomViewModel>();
        for (var entry : mergedChatRoom.channels.entrySet()) {
            if (!channels.containsKey(entry.getKey())) incoming.put(entry.getKey(), entry.getValue());
        }
        var incomingHistory = mergedChatRoom.getChatDataList().stream()
                .filter(chat -> incoming.containsKey(chat.getChannel()))
                .toList();
        mergeHistory(incomingHistory);
        channels.putAll(incoming);
        containerViewModel.getChatRoomList().remove(mergedChatRoom);
    }

    private void mergeHistory(List<ChatDataViewModel> incoming) {
        var mergedChats = new ArrayList<>(getChatDataList());
        mergedChats.addAll(incoming);
        mergedChats.sort(Comparator.comparing(chat -> chat.getChatData().firedAt()));
        var begin = Math.max(0, mergedChats.size() - getChatCacheSize());
        replaceChatHistory(mergedChats.subList(begin, mergedChats.size()));
    }

    public ObservableMap<TwitchChannelViewModel, SingleChatRoomViewModel> getChannels() {
        return channels;
    }

    @Override
    public boolean accept(TwitchChannel channel) {
        for (var channelViewModel : channels.keySet()) {
            if (channelViewModel.getChannel().equals(channel))
                return true;
        }
        return false;
    }

    @Override
    public void onStateUpdated(ChatRoomStateUpdatedEvent e) {
        var chatRoom = e.getChatRoom();

        channels.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getChannel().getJoinedChatRoom()
                        .filter(current -> current == chatRoom).isPresent())
                .findFirst()
                .map(Map.Entry::getValue)
                .ifPresent(chatRoomViewModel -> chatRoomViewModel.onStateUpdated(e));
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

        List.copyOf(channels.values()).forEach(SingleChatRoomViewModel::stopReceivingState);
        containerViewModel.onLeft(this);

        var exits = FXTaskGroup.startAll(channels.keySet(), TwitchChannelViewModel::leaveChatAsync);
        containerViewModel.trackExit(exits);
        return exits.task();
    }

}
