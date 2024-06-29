package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.domain.chat.ChatData;
import javafx.collections.ObservableList;

import java.util.function.Predicate;

class KeywordFilter implements Predicate<ChatData> {

    private final ObservableList<KeywordFilterEntry> entries;

    public KeywordFilter(ObservableList<KeywordFilterEntry> entries) {
        this.entries = entries;
    }

    @Override
    public boolean test(ChatData chatData) {
        return entries.stream().anyMatch(e -> e.test(chatData));
    }

}
