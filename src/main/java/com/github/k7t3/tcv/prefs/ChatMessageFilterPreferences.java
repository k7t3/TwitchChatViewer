package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.chat.filter.ChatMessageFilter;
import com.github.k7t3.tcv.app.chat.filter.KeywordMessageFilter;
import com.github.k7t3.tcv.app.chat.filter.UserChatMessageFilter;
import com.github.k7t3.tcv.domain.chat.ChatData;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.prefs.Preferences;

@SuppressWarnings("unused")
public class ChatMessageFilterPreferences extends PreferencesBase {

    private static final String FILTER_REGEX = "chat.filter.regex";
    private static final String FILTER_USER = "chat.filter.user";
    private static final byte[] DEFAULT_VALUE = new byte[0];

    private ReadOnlyObjectWrapper<ChatMessageFilter> chatMessageFilter;

    private ReadOnlyObjectWrapper<KeywordMessageFilter> keywordMessageFilter;

    private ReadOnlyObjectWrapper<UserChatMessageFilter> userChatMessageFilter;

    private final ReentrantLock syncLock = new ReentrantLock();

    ChatMessageFilterPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(FILTER_REGEX, DEFAULT_VALUE);
        defaults.put(FILTER_USER, DEFAULT_VALUE);
    }

    @Override
    protected void readFromPreferences() {
        var bytes = getByteArray(FILTER_REGEX);
        var filter = Arrays.equals(DEFAULT_VALUE, bytes)
                ? KeywordMessageFilter.DEFAULT
                : KeywordMessageFilter.deserialize(bytes);
        setKeywordMessageFilter(filter);
    }

    @Override
    public void writeToPreferences() {
        syncLock.lock();
        try {
            var regex = getKeywordMessageFilter();
            if (regex == null) {
                preferences.remove(FILTER_REGEX);
            } else {
                var bytes = regex.serialize();
                preferences.putByteArray(FILTER_REGEX, bytes);
            }

            var users = getUserChatMessageFilter();
            if (users == null) {
                preferences.remove(FILTER_USER);
            } else {
                var bytes = users.serialize();
                preferences.putByteArray(FILTER_USER, bytes);
            }
        } finally {
            syncLock.unlock();
        }
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyObjectWrapper<ChatMessageFilter> chatMessageFilterWrapper() {
        if (chatMessageFilter == null) {
            // 正規表現とユーザーIDのフィルタをマージ
            var regex = getKeywordMessageFilter();
            var user = getUserChatMessageFilter();
            var predicate = regex.and(user);
            var filter = new ChatMessageFilter() {

                @Override
                public boolean test(ChatData chatData) {
                    return predicate.test(chatData);
                }

                @Override
                public byte[] serialize() {
                    throw new UnsupportedOperationException();
                }

            };
            chatMessageFilter = new ReadOnlyObjectWrapper<>(filter);
        }
        return chatMessageFilter;
    }
    public ReadOnlyObjectProperty<ChatMessageFilter> chatMessageFilterProperty() { return chatMessageFilterWrapper().getReadOnlyProperty(); }
    public ChatMessageFilter getChatMessageFilter() { return chatMessageFilterWrapper().get(); }
    private void setChatMessageFilter(ChatMessageFilter chatMessageFilter) { chatMessageFilterWrapper().set(chatMessageFilter); }

    private ReadOnlyObjectWrapper<KeywordMessageFilter> keywordMessageFilterWrapper() {
        if (keywordMessageFilter == null) {
            var bytes = getByteArray(FILTER_REGEX);
            var filter = Arrays.equals(DEFAULT_VALUE, bytes)
                    ? KeywordMessageFilter.DEFAULT
                    : KeywordMessageFilter.deserialize(bytes);
            keywordMessageFilter = new ReadOnlyObjectWrapper<>(filter);
            keywordMessageFilter.addListener((ob, o, n) -> {
                if (n != null)
                    preferences.putByteArray(FILTER_REGEX, n.serialize());
            });
        }
        return keywordMessageFilter;
    }
    public ReadOnlyObjectProperty<KeywordMessageFilter> keywordMessageFilterProperty() { return keywordMessageFilterWrapper().getReadOnlyProperty(); }
    public KeywordMessageFilter getKeywordMessageFilter() { return keywordMessageFilterWrapper().get(); }
    private void setKeywordMessageFilter(KeywordMessageFilter keywordMessageFilter) { keywordMessageFilterWrapper().set(keywordMessageFilter); }

    private ReadOnlyObjectWrapper<UserChatMessageFilter> userChatMessageFilterWrapper() {
        if (userChatMessageFilter == null) {
            var bytes = getByteArray(FILTER_USER);
            var filter = Arrays.equals(DEFAULT_VALUE, bytes)
                    ? UserChatMessageFilter.DEFAULT
                    : UserChatMessageFilter.deserialize(bytes);
            userChatMessageFilter = new ReadOnlyObjectWrapper<>(filter);
            userChatMessageFilter.addListener((ob, o, n) -> {
                if (n != null)
                    preferences.putByteArray(FILTER_USER, n.serialize());
            });
        }
        return userChatMessageFilter;
    }
    public ReadOnlyObjectProperty<UserChatMessageFilter> userChatMessageFilterProperty() { return userChatMessageFilterWrapper().getReadOnlyProperty(); }
    public UserChatMessageFilter getUserChatMessageFilter() { return userChatMessageFilterWrapper().get(); }
    private void setUserChatMessageFilter(UserChatMessageFilter userChatMessageFilter) { userChatMessageFilterWrapper().set(userChatMessageFilter); }

}
