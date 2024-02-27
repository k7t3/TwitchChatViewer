package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.chat.RegexChatIgnoreFilter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Arrays;
import java.util.Map;
import java.util.prefs.Preferences;

public class ChatIgnoreFilterPreferences extends PreferencesBase {

    private static final String FILTER = "chat.filter";
    private static final byte[] DEFAULT_VALUE = new byte[0];

    private ObjectProperty<RegexChatIgnoreFilter> chatFilter;

    ChatIgnoreFilterPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(FILTER, DEFAULT_VALUE);
    }

    public void sync() {
        var filter = getChatFilter();

        if (filter == null) {
            preferences.remove(FILTER);
            return;
        }

        var bytes = filter.serialize();
        preferences.putByteArray(FILTER, bytes);
    }

    public ObjectProperty<RegexChatIgnoreFilter> chatFilterProperty() {
        if (chatFilter == null) {
            var bytes = getByteArray(FILTER);
            var filter = Arrays.equals(DEFAULT_VALUE, bytes)
                    ? RegexChatIgnoreFilter.DEFAULT
                    : RegexChatIgnoreFilter.deserialize(bytes);
            chatFilter = new SimpleObjectProperty<>(filter);
            chatFilter.addListener((ob, o, n) -> {
                if (n != null)
                    preferences.putByteArray(FILTER, n.serialize());
            });
        }
        return chatFilter;
    }
    public RegexChatIgnoreFilter getChatFilter() { return chatFilterProperty().get(); }
    public void setChatFilter(RegexChatIgnoreFilter chatFilter) { chatFilterProperty().set(chatFilter); }
}
