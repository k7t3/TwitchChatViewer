package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.domain.chat.ChatData;
import javafx.collections.ObservableList;

import java.util.function.Predicate;

class UserFilter implements Predicate<ChatData> {

    private final ObservableList<UserFilterEntry> entries;

    public UserFilter(ObservableList<UserFilterEntry> entries) {
        this.entries = entries;
    }

    @Override
    public boolean test(ChatData chatData) {
        return entries.stream().anyMatch(e -> e.test(chatData));
    }
}
