package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class ChatFilterSavingTest {
    @Test void queuedWritesUseCapturedValuesAndDoNotPublishIntoAClosedModel(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var store = new ChatFilterEntityStore(db);
            var queue = new LinkedBlockingQueue<Runnable>();
            var filters = new ChatFilters(store, queue::add, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            robot.interact(() -> {
                var entry = KeywordFilterEntry.exactMatch("accepted");
                filters.saveKeywordFilter(entry);
                entry.setKeyword("changed later");
                filters.close();
            });
            worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(filters.getKeywordEntries()).isEmpty());
            assertThat(store.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("accepted");
        }
    }

    @Test void aLateLoadOrCommitCannotReplaceTheNewestCompleteSnapshot(FxRobot robot) {
        try (var db = new TestDBConnector()) {
            db.connect();
            var store = new ChatFilterEntityStore(db);
            var before = store.read();
            var first = store.save(new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(0, 0, "first")), Set.of(), List.of(), Set.of()), () -> {});
            var second = store.save(new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(0, 0, "second")), Set.of(), List.of(), Set.of()), () -> {});
            var filters = new ChatFilters(store, Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            try {
                robot.interact(() -> {
                    filters.applyCommittedSnapshot(second);
                    filters.applyCommittedSnapshot(first);
                    filters.applySnapshot(ChatFilters.Snapshot.from(before));
                    assertThat(filters.getKeywordEntries()).extracting(KeywordFilterEntry::getKeyword).containsExactlyInAnyOrder("first", "second");
                });
            } finally { robot.interact(filters::close); }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void reentrantCloseOrLogoutDuringKeywordPublicationCannotRepopulateTheUserList(boolean logout, FxRobot robot) {
        var filters = new ChatFilters(org.mockito.Mockito.mock(ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        robot.interact(() -> {
            filters.getKeywordEntries().addListener((javafx.collections.ListChangeListener<KeywordFilterEntry>) change -> {
                if (!filters.getKeywordEntries().isEmpty()) {
                    if (logout) filters.onLogout(); else filters.close();
                }
            });
            filters.applyCommittedSnapshot(new ChatFilterEntityStore.Snapshot(List.of(new ChatKeywordFilterEntity(1, 0, "blocked")),
                    List.of(new com.github.k7t3.tcv.entity.ChatUserFilterEntity("42", "Name", "comment")), 1));
            assertThat(filters.getKeywordEntries()).isEmpty();
            assertThat(filters.getUserEntries()).isEmpty();
            filters.close();
        });
    }

    @Test void filteringNotificationsFollowPersistenceAndObservablePublication(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var store = new ChatFilterEntityStore(db);
            var queue = new LinkedBlockingQueue<Runnable>();
            var filters = new ChatFilters(store, queue::add, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            var notifications = new java.util.concurrent.atomic.AtomicInteger();
            try {
                robot.interact(() -> {
                    filters.subscribe(com.github.k7t3.tcv.app.event.KeywordFilteringEvent.class, event -> {
                        assertThat(javafx.application.Platform.isFxApplicationThread()).isTrue();
                        assertThat(filters.getKeywordEntries()).contains(event.entry());
                        assertThat(store.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).contains("accepted");
                        notifications.incrementAndGet();
                    });
                    filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("accepted"));
                    assertThat(notifications).hasValue(0);
                    assertThat(filters.getKeywordEntries()).isEmpty();
                });
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                robot.interact(() -> assertThat(notifications).hasValue(1));
            } finally { robot.interact(filters::close); }
        }
    }
}
