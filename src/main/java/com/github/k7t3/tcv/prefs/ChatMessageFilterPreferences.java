package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.chat.RegexChatMessageFilter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;
import java.util.Map;
import java.util.prefs.Preferences;

public class ChatMessageFilterPreferences extends PreferencesBase {

    private static final String FILTER = "chat.filter";
    private static final byte[] DEFAULT_VALUE = new byte[0];

    private ObjectProperty<RegexChatMessageFilter> messageFilter;

    ChatMessageFilterPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(FILTER, DEFAULT_VALUE);
    }

    @Override
    protected void onImported() {
        var bytes = getByteArray(FILTER);
        var filter = Arrays.equals(DEFAULT_VALUE, bytes)
                ? RegexChatMessageFilter.DEFAULT
                : RegexChatMessageFilter.deserialize(bytes);
        setMessageFilter(filter);
    }

    public void sync() {
        var filter = getMessageFilter();

        if (filter == null) {
            preferences.remove(FILTER);
            return;
        }

        var bytes = filter.serialize();
        preferences.putByteArray(FILTER, bytes);
    }

    public ObjectProperty<RegexChatMessageFilter> messageFilterProperty() {
        if (messageFilter == null) {
            var bytes = getByteArray(FILTER);
            var filter = Arrays.equals(DEFAULT_VALUE, bytes)
                    ? RegexChatMessageFilter.DEFAULT
                    : RegexChatMessageFilter.deserialize(bytes);
            messageFilter = new SimpleObjectProperty<>(filter);
            messageFilter.addListener((ob, o, n) -> {
                if (n != null)
                    preferences.putByteArray(FILTER, n.serialize());
            });
        }
        return messageFilter;
    }
    public RegexChatMessageFilter getMessageFilter() { return messageFilterProperty().get(); }
    public void setMessageFilter(RegexChatMessageFilter messageFilter) { messageFilterProperty().set(messageFilter); }
}
