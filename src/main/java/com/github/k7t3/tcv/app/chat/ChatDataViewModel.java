package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatDataViewModel implements ViewModel {

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyStringWrapper displayName = new ReadOnlyStringWrapper();

    private final ReadOnlyObjectWrapper<Color> color = new ReadOnlyObjectWrapper<>(null);

    private final ReadOnlyObjectWrapper<ChatMessage> message = new ReadOnlyObjectWrapper<>();

    private final ObservableList<Image> badges = FXCollections.observableArrayList();

    private final BooleanProperty visibleName = new SimpleBooleanProperty(true);

    private final BooleanProperty visibleBadge = new SimpleBooleanProperty(true);

    private final BooleanProperty deleted = new SimpleBooleanProperty(false);

    private final ObjectProperty<Font> font = new SimpleObjectProperty<>(null);

    private final ChatData chatData;

    private final GlobalChatBadgeStore globalBadgeStore;

    private final ChannelChatBadgeStore channelBadgeStore;

    private final ChannelEmoteStore emoteStore;

    private final DefinedChatColors definedChatColors;

    ChatDataViewModel(
            ChatData chatData,
            GlobalChatBadgeStore globalBadgeStore,
            ChannelChatBadgeStore channelBadgeStore,
            ChannelEmoteStore emoteStore,
            DefinedChatColors definedChatColors
    ) {
        this.chatData = chatData;
        this.globalBadgeStore = globalBadgeStore;
        this.channelBadgeStore = channelBadgeStore;
        this.emoteStore = emoteStore;
        this.definedChatColors = definedChatColors;
        update();
    }

    public ChatData getChatData() {
        return chatData;
    }

    private void update() {
        userName.set(chatData.userName());
        displayName.set(chatData.userDisplayName());
        message.set(chatData.message());

        // カラーがnullのときはランダム
        var color = (chatData.colorCode() == null)
                ? definedChatColors.getRandom()
                : Color.web(chatData.colorCode());
        this.color.set(color);

        var badges = new ArrayList<Image>();
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

    public ChannelEmoteStore getEmoteStore() {
        return emoteStore;
    }

    public ObservableList<Image> getBadges() {
        return badges;
    }

    public void copyMessage() {
        var cb = Clipboard.getSystemClipboard();

        var message = getMessage().stream()
                .map(ChatMessage.MessageFragment::fragment)
                .collect(Collectors.joining());

        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, message));
    }

    // ********** PROPERTIES **********

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

    public ObjectProperty<Font> fontProperty() { return font; }
    public Font getFont() { return font.get(); }
    public void setFont(Font font) { this.font.set(font); }
}
