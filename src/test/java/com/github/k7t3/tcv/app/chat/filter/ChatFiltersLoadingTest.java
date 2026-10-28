package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class ChatFiltersLoadingTest {

    @ParameterizedTest
    @CsvSource({"0, CONTAINS", "1, PREFIX_MATCH", "2, EXACT_MATCH", "3, REGEXP"})
    void applyingSnapshotReplacesBothListsWithStoredValues(int storedType, KeywordFilterType expectedType, FxRobot robot) {
        var model = new ChatFilters(mock(ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var snapshot = new ChatFilters.Snapshot(List.of(new ChatKeywordFilterEntity(7, storedType, "prefix")),
                List.of(new ChatUserFilterEntity("42", "user", "comment")));
        try {
            robot.interact(() -> {
                model.applySnapshot(snapshot);

                assertThat(model.getKeywordEntries()).hasSize(1);
                var keyword = model.getKeywordEntries().getFirst();
                assertThat(keyword.getFilterId()).isEqualTo(7);
                assertThat(keyword.getFilterType()).isEqualTo(expectedType);
                assertThat(keyword.getKeyword()).isEqualTo("prefix");
                if (expectedType == KeywordFilterType.REGEXP) {
                    assertThat(keyword.getPattern().pattern()).isEqualTo("prefix");
                }
                assertThat(model.getUserEntries()).extracting(UserFilterEntry::getUserId,
                        UserFilterEntry::getUserName, UserFilterEntry::getComment)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("42", "user", "comment"));
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void readingSnapshotOnAWorkerDoesNotChangeObservableLists(FxRobot robot) throws Exception {
        var store = mock(ChatFilterEntityStore.class);
        var keywordData = new ChatKeywordFilterEntity(7, 0, "new keyword");
        var userData = new ChatUserFilterEntity("42", "new user", "new comment");
        when(store.read()).thenReturn(new ChatFilterEntityStore.Snapshot(List.of(keywordData), List.of(userData), 0));
        var model = new ChatFilters(store, Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var previous = KeywordFilterEntry.containsMatch("previous keyword");
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> model.getKeywordEntries().add(previous));

            var snapshot = worker.submit(model::readSnapshot).get(5, TimeUnit.SECONDS);

            assertThat(snapshot.keywords()).containsExactly(keywordData);
            assertThat(snapshot.users()).containsExactly(userData);
            robot.interact(() -> {
                assertThat(model.getKeywordEntries()).containsExactly(previous);
                assertThat(model.getUserEntries()).isEmpty();
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void failedUserFilterReadPreservesBothPreviouslyLoadedLists(FxRobot robot) {
        var store = mock(ChatFilterEntityStore.class);
        var failure = new IllegalStateException("user filters could not be read");
        when(store.read()).thenThrow(failure);
        var model = new ChatFilters(store, Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var keyword = KeywordFilterEntry.containsMatch("previous keyword");
        var user = new UserFilterEntry("42", "previous user", "previous comment");
        try {
            robot.interact(() -> {
                model.getKeywordEntries().add(keyword);
                model.getUserEntries().add(user);

                assertThatThrownBy(model::readSnapshot).isSameAs(failure);

                assertThat(model.getKeywordEntries()).containsExactly(keyword);
                assertThat(model.getUserEntries()).containsExactly(user);
            });
        } finally {
            robot.interact(model::close);
        }
    }
}
