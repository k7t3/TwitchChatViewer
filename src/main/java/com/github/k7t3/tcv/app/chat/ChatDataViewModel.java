package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;
import com.github.k7t3.tcv.app.emoji.ChatEmojiStore;
import com.github.k7t3.tcv.app.event.KeywordFilteringEvent;
import com.github.k7t3.tcv.app.event.UserFilteringEvent;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.view.chat.ChatFont;
import de.saxsys.mvvmfx.MvvmFX;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Map;

/**
 * チャットメッセージのViewModel
 */
public class ChatDataViewModel {

    private final ReadOnlyObjectWrapper<TwitchChannelViewModel> channel;

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper displayName = new ReadOnlyStringWrapper();

    private final ReadOnlyObjectWrapper<Color> color = new ReadOnlyObjectWrapper<>(null);

    private final ReadOnlyObjectWrapper<ChatMessage> message = new ReadOnlyObjectWrapper<>();

    private final ObservableList<LazyImage> badges = FXCollections.observableArrayList();

    private final BooleanProperty visibleName = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleBadge = new SimpleBooleanProperty(true);

    private final BooleanProperty deleted = new SimpleBooleanProperty(false);

    private final BooleanProperty hidden = new SimpleBooleanProperty(false);

    private final ObjectProperty<ChatFont> font = new SimpleObjectProperty<>(null);

    private final ChatData chatData;

    private final GlobalChatBadgeStore globalBadgeStore;

    private final ChannelChatBadgeStore channelBadgeStore;

    private final ChatEmoteStore emoteStore;

    private final DefinedChatColors definedChatColors;

    private final ChatEmojiStore emojiStore;

    private final ChatFilters chatFilters;

    private boolean system = false;
    private boolean subs = false;
    private int bits = Integer.MIN_VALUE;

    ChatDataViewModel(
            TwitchChannelViewModel channel,
            ChatData chatData,
            GlobalChatBadgeStore globalBadgeStore,
            ChannelChatBadgeStore channelBadgeStore,
            ChatEmoteStore emoteStore,
            DefinedChatColors definedChatColors,
            ChatEmojiStore emojiStore,
            ChatFilters chatFilters
    ) {
        this.channel = new ReadOnlyObjectWrapper<>(channel);
        this.chatData = chatData;
        this.globalBadgeStore = globalBadgeStore;
        this.channelBadgeStore = channelBadgeStore;
        this.emoteStore = emoteStore;
        this.definedChatColors = definedChatColors;
        this.emojiStore = emojiStore;
        this.chatFilters = chatFilters;
        update();
    }

    public ChatData getChatData() {
        return chatData;
    }

    public LazyImage getEmojiImage(ChatMessageFragment fragment) {
        if (fragment.type() != ChatMessageFragment.Type.EMOJI) throw new IllegalArgumentException("fragment is not emoji");
        return emojiStore.get(fragment.additional());
    }

    private void update() {
        userName.set(chatData.userName());
        displayName.set(chatData.userDisplayName() != null ? chatData.userDisplayName() : "");
        message.set(chatData.message());

        // カラーがnullのときはランダム
        var color = (chatData.colorCode() == null)
                ? definedChatColors.getRandom(chatData.userId())
                : Color.web(chatData.colorCode());
        this.color.set(color);

        var badges = new ArrayList<LazyImage>();
        for (var chatBadge : chatData.badges()) {
            var i = channelBadgeStore.getNullable(chatBadge);
            if (i.isPresent()) {
                badges.add(i.get());
                continue;
            }
            globalBadgeStore.getNullable(chatBadge).ifPresent(badges::add);
        }
        this.badges.setAll(badges);
    }

    public ChatEmoteStore getEmoteStore() {
        return emoteStore;
    }

    public ObservableList<LazyImage> getBadges() {
        return badges;
    }

    public void copyMessage() {
        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, getMessage().getPlain()));
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean isSubs() {
        return subs;
    }

    public void setSubs(boolean subs) {
        this.subs = subs;
    }

    /**
     * チアーしたビッツ
     * @param bits ビッツ
     */
    public void setBits(int bits) {
        this.bits = bits;
    }

    public int getBits() {
        return bits;
    }

    public boolean isCheered() {
        return bits != Integer.MIN_VALUE;
    }

    public void keywordFilter() {
        var message = getMessage().getPlain();
        var filter = KeywordFilterEntry.exactMatch(message);
        chatFilters.saveKeywordFilter(filter);

        var event = new KeywordFilteringEvent(filter);
        MvvmFX.getNotificationCenter().publish(event.getClass().getName(), event);
    }

    public void userFilter(String comment) {
        var chat = getChatData();
        var filter = new UserFilterEntry(chat.userId(), chat.userName(), comment);
        chatFilters.saveUserFilter(filter);

        var event = new UserFilteringEvent(filter);
        MvvmFX.getNotificationCenter().publish(event.getClass().getName(), event);
    }

    // ********** PROPERTIES **********

    public ReadOnlyObjectProperty<TwitchChannelViewModel> channelProperty() { return channel.getReadOnlyProperty(); }
    public TwitchChannelViewModel getChannel() { return channel.get(); }

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }
    private void setUserName(String userName) { this.userName.set(userName); }

    private ReadOnlyStringWrapper displayNameWrapper() { return displayName; }
    public ReadOnlyStringProperty displayNameProperty() { return displayName.getReadOnlyProperty(); }
    public String getDisplayName() { return displayName.get(); }
    private void setDisplayName(String displayName) { this.displayName.set(displayName); }

    private ReadOnlyObjectWrapper<Color> colorWrapper() { return color; }
    public ReadOnlyObjectProperty<Color> colorProperty() { return color.getReadOnlyProperty(); }
    public Color getColor() { return color.get(); }
    private void setColor(Color color) { this.color.set(color); }

    private ReadOnlyObjectWrapper<ChatMessage> messageWrapper() { return message; }
    public ReadOnlyObjectProperty<ChatMessage> messageProperty() { return message.getReadOnlyProperty(); }
    public ChatMessage getMessage() { return message.get(); }
    private void setMessage(ChatMessage message) { this.message.set(message); }

    public BooleanProperty visibleNameProperty() { return visibleName; }
    public boolean isVisibleName() { return visibleName.get(); }
    public void setVisibleName(boolean deleted) { this.visibleName.set(deleted); }

    public BooleanProperty visibleBadgeProperty() { return visibleBadge; }
    public boolean isVisibleBadge() { return visibleBadge.get(); }
    public void setVisibleBadge(boolean visibleBadge) { this.visibleBadge.set(visibleBadge); }

    public BooleanProperty deletedProperty() { return deleted; }
    public boolean isDeleted() { return deleted.get(); }
    public void setDeleted(boolean deleted) { this.deleted.set(deleted); }

    public BooleanProperty hiddenProperty() { return hidden; }
    public boolean isHidden() { return hidden.get(); }
    public void setHidden(boolean hidden) { this.hidden.set(hidden); }

    public ObjectProperty<ChatFont> fontProperty() { return font; }
    public ChatFont getFont() { return font.get(); }
    public void setFont(ChatFont font) { this.font.set(font); }
}
