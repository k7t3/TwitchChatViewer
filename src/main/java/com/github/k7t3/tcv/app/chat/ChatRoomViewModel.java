package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.filter.ChatMessageFilter;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomListener;
import com.github.k7t3.tcv.view.chat.ChatFont;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.LinkedList;

public abstract class ChatRoomViewModel implements ChatRoomListener, TwitchChannelListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRoomViewModel.class);

    private final IntegerProperty chatCacheSize = new SimpleIntegerProperty(256);

    private final ObservableList<ChatDataViewModel> chatDataList = FXCollections.observableArrayList(new LinkedList<>());

    private final BooleanProperty autoScroll = new SimpleBooleanProperty(true);

    private final BooleanProperty showBadges = new SimpleBooleanProperty(true);

    private final BooleanProperty showName = new SimpleBooleanProperty(true);

    private final ObjectProperty<ChatFont> font = new SimpleObjectProperty<>();

    private final ObjectProperty<ChatMessageFilter> chatMessageFilter = new SimpleObjectProperty<>(ChatMessageFilter.DEFAULT);

    private final ReadOnlyBooleanWrapper chatJoined = new ReadOnlyBooleanWrapper(false);

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    private final BooleanProperty selectMode = new SimpleBooleanProperty(false);

    private final GlobalChatBadgeStore globalChatBadgeStore;

    private final ChatEmoteStore emoteStore;

    private final DefinedChatColors definedChatColors;

    protected final ChatRoomContainerViewModel containerViewModel;

    ChatRoomViewModel(
            GlobalChatBadgeStore globalChatBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            ChatRoomContainerViewModel containerViewModel) {
        this.globalChatBadgeStore = globalChatBadgeStore;
        this.emoteStore = emoteStore;
        this.definedChatColors = definedChatColors;
        this.containerViewModel = containerViewModel;

        chatCacheSize.addListener((ob, o, n) -> itemCountLimitChanged(n.intValue()));
    }

    public void popOutAsFloatableStage() {
        containerViewModel.popOutAsFloatableStage(this);
    }

    public void restoreToContainer() {
        containerViewModel.restoreToContainer(this);
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

    abstract boolean hasChannel(TwitchChannelViewModel channel);

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

    protected void addChat(ChatDataViewModel chat) {
        Platform.runLater(() -> {

            chat.visibleNameProperty().bind(showName);
            chat.visibleBadgeProperty().bind(showBadges);
            chat.fontProperty().bind(font);

            // 上限制限
            if (getChatCacheSize() <= chatDataList.size()) {
                chatDataList.removeFirst();
            }
            chatDataList.add(chat);
        });
    }

    @Override
    public void onChatDataPosted(ChatRoom chatRoom, ChatData item) {
        try {
            var filter = getChatMessageFilter();
            var hidden = !filter.test(item); // Filterをパスしないときは非表示

            TwitchChannelViewModel channel;
            try {
                channel = getChannel(chatRoom.getChannel());
            } catch (ConcurrentModificationException ignored) {
                // チャンネルが閉じたあとにチャットを受信した場合に発生する可能性がある
                return;
            }

            var chatData = createChatDataViewModel(channel, item);
            chatData.setHidden(hidden);
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
        Platform.runLater(() -> chatDataList.stream()
                .filter(c -> c.getChatData().msgId().equalsIgnoreCase(messageId))
                .findFirst()
                .ifPresent(chatData -> chatData.setDeleted(true)));
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
    public void onUserSubscribed(ChatRoom chatRoom, ChatData chatData) {
        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);
        viewModel.setSubs(true);

        addChat(viewModel);
    }

    @Override
    public void onUserGiftedSubscribe(ChatRoom chatRoom, String giverName, String userName) {
        var message = "%s sub gifted by %s.".formatted(userName, giverName);
        var chatData = ChatData.createSystemData(message);

        var channel = getChannel(chatRoom.getChannel());

        var viewModel = createChatDataViewModel(channel, chatData);
        viewModel.fontProperty().bind(font);
        viewModel.setSystem(true);

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
