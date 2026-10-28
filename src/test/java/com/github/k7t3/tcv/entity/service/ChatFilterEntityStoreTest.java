package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatFilterEntityStoreTest {
    private TestDBConnector connector;
    private ChatKeywordFilterEntityService keywords;
    private ChatUserFilterEntityService users;
    private ChatFilterEntityStore store;
    private int originalId;

    @BeforeEach void open() {
        connector = spy(new TestDBConnector());
        connector.connect();
        keywords = new ChatKeywordFilterEntityService(connector);
        users = new ChatUserFilterEntityService(connector);
        originalId = keywords.insert(new ChatKeywordFilterEntity(0, 0, "original"));
        users.save(new ChatUserFilterEntity("first", "First", "old"));
        users.save(new ChatUserFilterEntity("removed", "Removed", "old"));
        store = new ChatFilterEntityStore(connector);
        clearInvocations(connector);
    }
    @AfterEach void close() { connector.close(); }

    @Test
    void allFilterChangesAndTheCompanionWriteShareOneTransaction() {
        var companion = new AtomicInteger();
        var changes = new ChatFilterEntityStore.Changes(
                List.of(new ChatKeywordFilterEntity(originalId, 1, "changed"), new ChatKeywordFilterEntity(0, 3, "a.*")),
                Set.of(), List.of(new ChatUserFilterEntity("first", "First", "new")), Set.of("removed"));
        var result = store.save(changes, () -> {
            companion.incrementAndGet();
            assertThat(users.retrieveAll()).containsExactly(new ChatUserFilterEntity("first", "First", "new"));
        });
        assertThat(companion).hasValue(1);
        verify(connector).transaction(any());
        assertThat(result.revision()).isEqualTo(1);
        assertThat(result.keywords()).hasSize(2);
        assertThat(result.keywords().stream().filter(row -> row.keyword().equals("a.*")).findFirst().orElseThrow().id()).isPositive();
        assertThat(result).isEqualTo(store.read());
    }

    @Test
    void aUserWriteFailureRollsBackEarlierKeywordChangesAndDoesNotRunTheCompanion() {
        connector.execute("""
                create trigger reject_filter before insert on chat_user_filter
                when new.comment = 'reject'
                begin select raise(abort, 'rejected filter'); end;
                """);
        connector.commit();
        var original = store.read();
        var companion = new AtomicInteger();
        var changes = new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(originalId, 0, "changed")),
                Set.of(), List.of(new ChatUserFilterEntity("first", "First", "reject")), Set.of("removed"));
        assertThatThrownBy(() -> store.save(changes, companion::incrementAndGet)).hasMessageContaining("rejected filter");
        assertThat(store.read()).isEqualTo(original);
        assertThat(companion).hasValue(0);
    }

    @Test
    void aCompanionFailureRollsBackEveryFilterChangeAndKeepsTheOriginalCause() {
        var original = store.read();
        var failure = new IllegalStateException("preference flush failed");
        var changes = new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(0, 0, "new")),
                Set.of(originalId), List.of(), Set.of("first"));
        assertThatThrownBy(() -> store.save(changes, () -> { throw failure; })).isSameAs(failure);
        assertThat(store.read()).isEqualTo(original);
        var committed = store.save(changes, () -> {});
        assertThat(committed.revision()).isEqualTo(1);
        assertThat(committed.keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("new");
    }

    @Test
    void snapshotsAndSubmittedChangeListsDoNotFollowLaterMutations() {
        var previous = store.read();
        var submitted = new ArrayList<ChatKeywordFilterEntity>();
        submitted.add(new ChatKeywordFilterEntity(0, 0, "accepted"));
        var changes = new ChatFilterEntityStore.Changes(submitted, Set.of(), List.of(), Set.of());
        submitted.add(new ChatKeywordFilterEntity(0, 0, "too late"));
        var current = store.save(changes, () -> {});
        assertThat(previous.keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("original");
        assertThat(current.keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("original", "accepted");
        assertThatThrownBy(() -> current.keywords().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidPatternsAreRejectedBeforeAnyWrite() {
        var before = store.read();
        var changes = new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(originalId, 3, "[")),
                Set.of(), List.of(), Set.of());
        assertThatThrownBy(() -> store.save(changes, () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.read()).isEqualTo(before);
    }
}
