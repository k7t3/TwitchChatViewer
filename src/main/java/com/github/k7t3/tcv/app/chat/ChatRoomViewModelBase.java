package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.view.core.Resources;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Objects;

public abstract class ChatRoomViewModelBase implements ChatRoomListener, TwitchChannelListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomViewModelBase.class);

    private final IntegerProperty itemCountLimit = new SimpleIntegerProperty(256);

    private final ObservableList<ChatDataViewModel> chatDataList = FXCollections.observableArrayList(new LinkedList<>());

    private final BooleanProperty autoScroll = new SimpleBooleanProperty(true);

    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);

    private final BooleanProperty showName = new SimpleBooleanProperty(true);

    private final ObjectProperty<Font> font = new SimpleObjectProperty<>();

    private final ObjectProperty<ChatMessageFilter> chatMessageFilter = new SimpleObjectProperty<>(ChatMessageFilter.DEFAULT);

    private final ReadOnlyBooleanWrapper chatJoined = new ReadOnlyBooleanWrapper(false);

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    private final BooleanProperty selectMode = new SimpleBooleanProperty(false);

    private final GlobalChatBadgeStore globalChatBadgeStore;

    private final ChatEmoteStore emoteStore;

    private final DefinedChatColors definedChatColors;

    ChatRoomViewModelBase(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors
    ) {
        this.globalChatBadgeStore = globalChatBadgeStore;
        this.emoteStore = emoteStore;
        this.definedChatColors = definedChatColors;

        itemCountLimit.addListener((ob, o, n) -> itemCountLimitChanged(n.intValue()));
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

    abstract boolean hasChannel(TwitchChannel channel);

    protected void addChat(ChatDataViewModel chat) {
        Platform.runLater(() -> {
            // 上限制限
            if (getItemCountLimit() <= chatDataList.size()) {
                chatDataList.removeFirst();
            }
            chatDataList.add(chat);
        });
    }

    @SuppressWarnings("UnusedReturnValue")
    public abstract FXTask<?> joinChatAsync();

    @SuppressWarnings("UnusedReturnValue")
    public abstract FXTask<?> leaveChatAsync();

    protected abstract TwitchChannelViewModel getChannel(TwitchChannel channel);

    private ChatDataViewModel createChatDataViewModel(TwitchChannelViewModel channel, ChatData item) {
        return new ChatDataViewModel(
                channel,
                item,
                globalChatBadgeStore,
                channel.getChatBadgeStore(),
                emoteStore,
                definedChatColors
        );
    }

    @Override
    public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
        try {
            var filter = getChatMessageFilter();
            if (!filter.test(item)) {
                return;
            }

            TwitchChannelViewModel channel;
            try {
                channel = getChannel(chatRoom.getChannel());
            } catch (ConcurrentModificationException ignored) {
                // チャンネルの分離時に発生する可能性がある
                return;
            }

            var chatData = createChatDataViewModel(channel, item);
            chatData.visibleNameProperty().bind(showName);
            chatData.visibleBadgeProperty().bind(showBadges);
            chatData.fontProperty().bind(font);

            addChat(chatData);
        } catch (Exception e) {
            LOGGER.error(item.toString(), e);
        }
    }

    @Override
    public void onChatCleared(ChatRoom chatRoom) {
        LOGGER.info("{} chat cleared", chatRoom.getBroadcaster().getUserLogin());

        var channelId = chatRoom.getBroadcaster().getUserLogin();

        Platform.runLater(() ->
                chatDataList.removeIf(c -> c.getChatData().channelId().equalsIgnoreCase(channelId)));
    }

    @Override
    public void onChatMessageDeleted(ChatRoom chatRoom, String messageId) {
        LOGGER.info("{} chat deleted", chatRoom.getBroadcaster().getUserLogin());
        Platform.runLater(() ->
                chatDataList.removeIf(c -> c.getChatData().msgId().equalsIgnoreCase(messageId)));
    }

    @Override
    public void onRaidReceived(ChatRoom chatRoom, String raiderName, int viewerCount) {
        var format = Resources.getString("chat.raid.received.format");
        var chatData = ChatData.createSystemData(format.formatted(raiderName, viewerCount));

        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);

        addChat(viewModel);
    }

    @Override
    public void onUserSubscribed(ChatRoom chatRoom, String userName) {
        var message = "%s subscribed.".formatted(userName);
        var chatData = ChatData.createSystemData(message);

        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);

        addChat(viewModel);
    }

    @Override
    public void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName) {
        var message = "%s sub gifted by %s.".formatted(userName, giverName);
        var chatData = ChatData.createSystemData(message);

        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);

        addChat(viewModel);
    }

    // ******************** PROPERTIES ********************

    public IntegerProperty itemCountLimitProperty() { return itemCountLimit; }
    public int getItemCountLimit() { return itemCountLimit.get(); }
    public void setItemCountLimit(int itemCountLimit) { this.itemCountLimit.set(itemCountLimit); }

    public BooleanProperty autoScrollProperty() { return autoScroll; }
    public boolean isAutoScroll() { return autoScroll.get(); }
    public void setAutoScroll(boolean autoScroll) { this.autoScroll.set(autoScroll); }

    public BooleanProperty showBadgesProperty() { return showBadges; }
    public boolean isShowBadges() { return showBadges.get(); }
    public void setShowBadges(boolean showBadges) { this.showBadges.set(showBadges); }

    public BooleanProperty showNameProperty() { return showName; }
    public boolean isShowName() { return showName.get(); }
    public void setShowName(boolean showName) { this.showName.set(showName); }

    public ObjectProperty<Font> fontProperty() { return font; }
    public Font getFont() { return font.get(); }
    public void setFont(Font font) { this.font.set(font); }

    public ObjectProperty<ChatMessageFilter> chatMessageFilterProperty() { return chatMessageFilter; }
    public ChatMessageFilter getChatMessageFilter() { return chatMessageFilter.get(); }
    public void setChatMessageFilter(ChatMessageFilter chatMessageFilter) { this.chatMessageFilter.set(chatMessageFilter); }

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
