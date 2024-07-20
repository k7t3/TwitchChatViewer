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

package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatKeywordFilterEntityService;
import com.github.k7t3.tcv.entity.service.ChatUserFilterEntityService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ChatFilters extends AbstractViewModel {

    private final ChatKeywordFilterEntityService keywordService;
    private final ChatUserFilterEntityService userService;

    private final ObservableList<KeywordFilterEntry> keywordEntries = FXCollections.observableArrayList();
    private final ObservableList<UserFilterEntry> userEntries = FXCollections.observableArrayList();

    private final Predicate<ChatData> filter;

    public ChatFilters(ChatKeywordFilterEntityService keywordService, ChatUserFilterEntityService userService) {
        this.keywordService = keywordService;
        this.userService = userService;
        filter = new KeywordFilter(keywordEntries).or(new UserFilter(userEntries));
    }

    public Predicate<ChatData> getFilter() {
        return filter;
    }

    public ObservableList<KeywordFilterEntry> getKeywordEntries() {
        return keywordEntries;
    }

    public ObservableList<UserFilterEntry> getUserEntries() {
        return userEntries;
    }

    public void loadAll() {
        var ke = keywordService.retrieveAll().stream().map(this::fromKeywordEntity).toList();
        keywordEntries.setAll(ke);

        var ue = userService.retrieveAll()
                .stream()
                .map(u -> new UserFilterEntry(u.userId(), u.userName(), u.comment()))
                .toList();
        userEntries.setAll(ue);
    }

    public void saveUserFilter(UserFilterEntry entry) {
        FXTask.task(() -> userService.save(toUserEntity(entry))).onDone(e -> {
            if (userEntries.stream().noneMatch(e2 -> e2.getUserId().equals(entry.getUserId()))) {
                userEntries.add(entry);
            }
        }).runAsync();
    }

    public FXTask<Void> deleteUserFilter(UserFilterEntry entry) {
        return FXTask.task(() -> userService.delete(toUserEntity(entry)))
                .onDone(entity -> userEntries.remove(entry))
                .runAsync();
    }

    private ChatUserFilterEntity toUserEntity(UserFilterEntry entry) {
        return new ChatUserFilterEntity(entry.getUserId(), entry.getUserName(), entry.getComment());
    }

    public void saveKeywordFilter(KeywordFilterEntry entry) {
        if (entry.getFilterId() < 1) {
            var t = FXTask.task(() -> keywordService.insert(toKeywordEntity(entry)));
            t.onDone(id -> {
               entry.setFilterId(id);
               keywordEntries.add(entry);
            }).runAsync();
        } else {
            FXTask.task(() -> keywordService.update(toKeywordEntity(entry))).runAsync();
        }
    }

    public FXTask<Void> deleteKeywordFilter(KeywordFilterEntry entry) {
        return FXTask.task(() -> keywordService.delete(toKeywordEntity(entry)))
                .onDone(entity -> keywordEntries.remove(entry))
                .runAsync();
    }

    private ChatKeywordFilterEntity toKeywordEntity(KeywordFilterEntry entry) {
        return new ChatKeywordFilterEntity(
                entry.getFilterId(),
                entry.getFilterType().getType(),
                entry.getKeyword()
        );
    }

    private KeywordFilterEntry fromKeywordEntity(ChatKeywordFilterEntity entity) {
        var type = KeywordFilterType.of(entity.type());
        var keyword = entity.keyword();
        var entry = switch (type) {
            case CONTAINS -> KeywordFilterEntry.containsMatch(keyword);
            case PREFIX_MATCH -> KeywordFilterEntry.prefixMatch(keyword);
            case EXACT_MATCH -> KeywordFilterEntry.exactMatch(keyword);
            case REGEXP -> KeywordFilterEntry.regexMatch(Pattern.compile(keyword));
        };
        entry.setFilterId(entity.id());
        return entry;
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        // no-op
    }

    @Override
    public void onLogout() {
        keywordEntries.clear();
        userEntries.clear();
    }

    @Override
    public void close() {
    }
}
