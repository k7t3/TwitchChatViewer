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
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.emoji.ChatEmojiStore;
import com.github.k7t3.tcv.app.event.KeywordFilteringEvent;
import com.github.k7t3.tcv.app.event.UserFilteringEvent;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.event.chat.*;
import com.github.k7t3.tcv.view.chat.ChatFont;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public abstract class ChatRoomViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomViewModel.class);

    private final IntegerProperty chatCacheSize = new SimpleIntegerProperty(256);

    private final ObservableList<ChatDataViewModel> chatDataList = FXCollections.observableArrayList(new LinkedList<>());

    private final BooleanProperty autoScroll = new SimpleBooleanProperty(true);

    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);

    private final BooleanProperty showName = new SimpleBooleanProperty(true);

    private final ObjectProperty<ChatFont> font = new SimpleObjectProperty<>();

    private final ReadOnlyBooleanWrapper chatJoined = new ReadOnlyBooleanWrapper(false);

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    private final BooleanProperty selectMode = new SimpleBooleanProperty(false);

    private final GlobalChatBadgeStore globalChatBadgeStore;

    private final ChatEmoteStore emoteStore;

    private final DefinedChatColors definedChatColors;

    private final ChatEmojiStore emojiStore;

    protected final ChatRoomContainerViewModel containerViewModel;

    private final ChatFilters chatFilters;

    ChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            ChatEmojiStore emojiStore,
            ChatRoomContainerViewModel containerViewModel,
            ChatFilters chatFilters) {
        this.globalChatBadgeStore = globalChatBadgeStore;
        this.emoteStore = emoteStore;
        this.definedChatColors = definedChatColors;
        this.emojiStore = emojiStore;
        this.containerViewModel = containerViewModel;
        this.chatFilters = chatFilters;

        chatCacheSize.addListener((ob, o, n) -> itemCountLimitChanged(n.intValue()));
    }

    public void popOutAsFloatableStage() {
        containerViewModel.popOutAsFloatableStage(this);
    }

    public void restoreToContainer() {
        containerViewModel.restoreToContainer(this);
    }

    public void chatFilter(KeywordFilteringEvent event) {
        var entry = event.entry();
        chatDataList.stream()
                .filter(c -> entry.test(c.getChatData()))
                .forEach(c -> c.setHidden(true));
    }

    public void chatFilter(UserFilteringEvent event) {
        var entry = event.entry();
        chatDataList.stream()
                .filter(c -> entry.test(c.getChatData()))
                .forEach(c -> c.setHidden(true));
    }

    private void itemCountLimitChanged(int limit) {
        var over = chatDataList.size() - limit;

        if (over < 1)
            return;

        for (var i = 0; i < over; i++) {
            chatDataList.removeFirst();
        }
    }

    public ObservableList<ChatDataViewModel> getChatDataList() {
        return chatDataList;
    }

    /**
     * このチャットルームを一意に表すメソッド。
     * FloatableStageの座標を記録するための識別子として使用する。
     */
    public abstract String getIdentity();

    @SuppressWarnings("UnusedReturnValue")
    public abstract FXTask<?> joinChatAsync();

    @SuppressWarnings("UnusedReturnValue")
    public abstract FXTask<?> leaveChatAsync();

    protected abstract TwitchChannelViewModel getChannel(TwitchChannel channel);

    protected abstract boolean accept(TwitchChannel channel);

    private ChatDataViewModel createChatDataViewModel(TwitchChannelViewModel channel, ChatData item) {
        return new ChatDataViewModel(
                channel,
                item,
                globalChatBadgeStore,
                channel.getChatBadgeStore(),
                emoteStore,
                definedChatColors,
                emojiStore,
                chatFilters
        );
    }

    public void addChat(ChatDataViewModel chat) {
        chat.visibleNameProperty().bind(showName);
        chat.visibleBadgeProperty().bind(showBadges);
        chat.fontProperty().bind(font);

        // 上限制限
        if (getChatCacheSize() <= chatDataList.size()) {
            chatDataList.removeFirst();
        }
        chatDataList.add(chat);
    }

    public void deleteChatMessage(String msgId) {
        for (var item : chatDataList) {
            if (item.getChatData().msgId().equalsIgnoreCase(msgId)) {
                item.setDeleted(true);
                break;
            }
        }
    }

    public void clearChatMessages(ChatRoom chatRoom) {
        var broadcaster = chatRoom.getBroadcaster();
        chatDataList.removeIf(chatData -> chatData.getChannel().getBroadcaster().equals(broadcaster));
    }

    public abstract void onStateUpdated(ChatRoomStateUpdatedEvent e);

    void onChatAdded(ChatMessageEvent e, boolean hidden) {
        var chatRoom = e.getChatRoom();
        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, e.getChatData());
        viewModel.fontProperty().bind(font);
        viewModel.setHidden(hidden);

        addChat(viewModel);
    }

    void onCheered(CheeredEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = getChannel(chatRoom.getChannel());
        var cheer = e.getCheer();

        var viewModel = createChatDataViewModel(channel, cheer.chatData());
        viewModel.fontProperty().bind(font);
        viewModel.setBits(cheer.bits());

        addChat(viewModel);
    }

    void onRaidReceived(RaidReceivedEvent e) {
        var format = Resources.getString("chat.raid.received.format");
        var chatData = ChatData.createSystemData(format.formatted(e.getRaiderName(), e.getViewerCount()));
        var channel = getChannel(e.getChatRoom().getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);
        viewModel.setSystem(true);

        addChat(viewModel);
    }

    void onGiftedSubsEvent(UserGiftedSubscribeEvent e) {
        var message = "%s sub gifted by %s.".formatted(e.getReceiverName(), e.getGiverName());
        var chatData = ChatData.createSystemData(message);

        var chatRoom = e.getChatRoom();
        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);
        viewModel.setSystem(true);

        addChat(viewModel);
    }

    void onUserSubsEvent(UserSubscribedEvent e) {
        var chatRoom = e.getChatRoom();
        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, e.getChatData());
        viewModel.fontProperty().bind(font);
        viewModel.setSubs(true);

        addChat(viewModel);
    }

    // ******************** PROPERTIES ********************

    public IntegerProperty chatCacheSizeProperty() { return chatCacheSize; }
    public int getChatCacheSize() { return chatCacheSize.get(); }
    public void setChatCacheSize(int chatCacheSize) { this.chatCacheSize.set(chatCacheSize); }

    public BooleanProperty autoScrollProperty() { return autoScroll; }
    public boolean isAutoScroll() { return autoScroll.get(); }
    public void setAutoScroll(boolean autoScroll) { this.autoScroll.set(autoScroll); }

    public BooleanProperty showBadgesProperty() { return showBadges; }
    public boolean isShowBadges() { return showBadges.get(); }
    public void setShowBadges(boolean showBadges) { this.showBadges.set(showBadges); }

    public BooleanProperty showNameProperty() { return showName; }
    public boolean isShowName() { return showName.get(); }
    public void setShowName(boolean showName) { this.showName.set(showName); }

    public ObjectProperty<ChatFont> fontProperty() { return font; }
    public ChatFont getFont() { return font.get(); }
    public void setFont(ChatFont font) { this.font.set(font); }

    protected ReadOnlyBooleanWrapper chatJoinedWrapper() { return chatJoined; }
    public ReadOnlyBooleanProperty chatJoinedProperty() { return chatJoined.getReadOnlyProperty(); }
    public boolean isChatJoined() { return chatJoined.get(); }
    protected void setChatJoined(boolean joined) { chatJoined.set(joined); }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean selected) { this.selected.set(selected); }

    public BooleanProperty selectModeProperty() { return selectMode; }
    public boolean isSelectMode() { return selectMode.get(); }
    public void setSelectMode(boolean selectMode) { this.selectMode.set(selectMode); }
}
