package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 両フィルターテーブルを一つの保存単位として扱う。接続は借用する。 */
public final class ChatFilterEntityStore {
    public record Changes(List<ChatKeywordFilterEntity> keywords, Set<Integer> deletedKeywords,
                          List<ChatUserFilterEntity> users, Set<String> deletedUsers) {
        public Changes {
            keywords = List.copyOf(keywords);
            deletedKeywords = Set.copyOf(deletedKeywords);
            users = List.copyOf(users);
            deletedUsers = Set.copyOf(deletedUsers);
        }
        public static Changes empty() { return new Changes(List.of(), Set.of(), List.of(), Set.of()); }
    }

    public record Snapshot(List<ChatKeywordFilterEntity> keywords, List<ChatUserFilterEntity> users, long revision) {
        public Snapshot { keywords = List.copyOf(keywords); users = List.copyOf(users); }
    }

    private final DBConnector connector;
    private final ChatKeywordFilterEntityService keywords;
    private final ChatUserFilterEntityService users;
    private final SettingsJournal settingsJournal;
    private long revision;

    public ChatFilterEntityStore(DBConnector connector) {
        this.connector = Objects.requireNonNull(connector);
        keywords = new ChatKeywordFilterEntityService(connector);
        users = new ChatUserFilterEntityService(connector);
        settingsJournal = new SettingsJournal(connector);
    }

    /** @return 同じ接続を借用する設定復旧情報。取得だけではI/Oしない */
    public SettingsJournal settingsJournal() { return settingsJournal; }

    public Snapshot read() {
        synchronized (connector) {
            var result = new Snapshot[1];
            connector.readTransaction(() -> result[0] =
                    new Snapshot(keywords.retrieveAll(), users.retrieveAll(), revision));
            return result[0];
        }
    }

    /** companion は SQL 変更後、commit 前に実行する。いずれかの失敗で SQL 全体を rollback する。 */
    public Snapshot save(Changes changes, Runnable companion) {
        validate(changes);
        Objects.requireNonNull(companion);
        synchronized (connector) {
            var result = new Snapshot[1];
            connector.transaction(() -> {
                for (var id : changes.deletedKeywords()) keywords.deleteInTransaction(new ChatKeywordFilterEntity(id, 0, ""));
                for (var entity : changes.keywords()) {
                    if (entity.id() == 0) keywords.insertInTransaction(entity);
                    else if (keywords.updateInTransaction(entity) != 1) throw new IllegalStateException("keyword filter no longer exists: " + entity.id());
                }
                for (var id : changes.deletedUsers()) users.deleteInTransaction(new ChatUserFilterEntity(id, "", ""));
                for (var entity : changes.users()) users.saveInTransaction(entity);
                result[0] = new Snapshot(keywords.retrieveAll(), users.retrieveAll(), revision + 1);
                companion.run();
            });
            revision++;
            return result[0];
        }
    }

    private static void validate(Changes changes) {
        Objects.requireNonNull(changes);
        var keywordIds = new HashSet<Integer>();
        for (var row : changes.keywords()) {
            if (row.id() < 0 || row.type() < 0 || row.type() > 3 || row.keyword() == null || row.keyword().isBlank()) {
                throw new IllegalArgumentException("invalid keyword filter");
            }
            if (row.id() > 0 && (!keywordIds.add(row.id()) || changes.deletedKeywords().contains(row.id()))) {
                throw new IllegalArgumentException("conflicting keyword filter changes");
            }
            if (row.type() == 3) Pattern.compile(row.keyword());
        }
        if (changes.deletedKeywords().stream().anyMatch(id -> id < 1)) throw new IllegalArgumentException("invalid deleted keyword id");
        var userIds = new HashSet<String>();
        for (var row : changes.users()) {
            if (row.userId() == null || row.userId().isBlank() || row.userName() == null || row.comment() == null
                    || !userIds.add(row.userId()) || changes.deletedUsers().contains(row.userId())) {
                throw new IllegalArgumentException("invalid or conflicting user filter changes");
            }
        }
        if (changes.deletedUsers().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("invalid deleted user id");
    }
}
