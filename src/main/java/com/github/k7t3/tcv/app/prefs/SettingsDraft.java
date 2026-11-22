package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** FX スレッドで確定する編集内容。保存中の画面の変更・破棄には影響されない。 */
public record SettingsDraft(Map<String, Optional<String>> values, ChatFilterEntityStore.Changes filters) {
    public SettingsDraft { values = Map.copyOf(values); Objects.requireNonNull(filters); }

    public static final class Builder {
        private final Map<String, Optional<String>> values = new HashMap<>();
        private final ArrayList<ChatKeywordFilterEntity> keywords = new ArrayList<>();
        private final HashSet<Integer> deletedKeywords = new HashSet<>();
        private final ArrayList<ChatUserFilterEntity> users = new ArrayList<>();
        private final HashSet<String> deletedUsers = new HashSet<>();

        public void values(Map<String, Optional<String>> values) { this.values.putAll(values); }
        public void keyword(ChatKeywordFilterEntity row) { keywords.add(row); }
        public void deleteKeyword(int id) { if (id > 0) deletedKeywords.add(id); }
        public void user(ChatUserFilterEntity row) { users.add(row); }
        public void deleteUser(String id) { deletedUsers.add(id); }
        public SettingsDraft build() {
            return new SettingsDraft(values, new ChatFilterEntityStore.Changes(keywords, deletedKeywords, users, deletedUsers));
        }
    }
}
