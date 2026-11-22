package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatFilterWriteOwnershipTest {
    @TempDir Path directory;

    @ParameterizedTest(name = "rejection during {0}")
    @ValueSource(strings = {"registration", "execution"})
    void rejectionLeavesNoPendingWrite(String boundary, FxRobot robot) {
        var store = mock(ChatFilterEntityStore.class);
        var failure = new RejectedExecutionException("application is stopping");
        var settled = new AtomicReference<CompletionStage<Void>>();
        var filters = new ChatFilters(store, command -> { throw failure; }, stage -> {
            settled.set(stage);
            if (boundary.equals("registration")) throw failure;
        }, unexpected -> { throw new AssertionError(unexpected); });
        try {
            robot.interact(() -> assertThatThrownBy(() -> filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("rejected")))
                    .isSameAs(failure));
            assertThat(settled.get().toCompletableFuture().isDone()).isTrue();
            assertThatCode(() -> settled.get().toCompletableFuture().join()).doesNotThrowAnyException();
            verifyNoInteractions(store);
        } finally { robot.interact(filters::close); }
    }

    @Test
    void aUiReportedFailureAllowsRetryAndIsNotReplayedByApplicationShutdown(FxRobot robot) throws Exception {
        var file = new UserDataFile(directory.resolve("retry.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        file.getConnector().execute("""
                CREATE TRIGGER fail_filter BEFORE INSERT ON chat_keyword_filter WHEN NEW.keyword = 'rejected'
                BEGIN SELECT RAISE(ABORT, 'injected filter failure'); END;
                """);
        file.getConnector().commit();
        var errors = new CopyOnWriteArrayList<Throwable>();
        var helper = new AppHelper(new TaskWorker(), mock(EventPublishers.class), errors::add);
        helper.setUserDataFile(file);
        var filters = helper.getChatFilters();
        var observation = new AtomicReference<FXTask<ChatFilterEntityStore.Snapshot>>();
        try {
            robot.interact(() -> observation.set(filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("rejected"))));
            var failure = catchThrowable(() -> observation.get().get(5, TimeUnit.SECONDS));
            assertThat(failure).hasRootCauseMessage("[SQLITE_CONSTRAINT_TRIGGER] A RAISE function within a trigger fired, causing the SQL statement to abort (injected filter failure)");
            robot.interact(() -> {
                assertThat(errors).containsExactly(failure.getCause());
                assertThat(filters.getKeywordEntries()).isEmpty();
                observation.set(filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("accepted")));
            });
            assertThat(observation.get().get(5, TimeUnit.SECONDS).keywords()).extracting(ChatKeywordFilterEntity::keyword)
                    .containsExactly("accepted");
            robot.interact(() -> assertThat(filters.getKeywordEntries()).extracting(KeywordFilterEntry::getKeyword)
                    .containsExactly("accepted"));

            assertThatCode(helper::close).doesNotThrowAnyException();

            try (var reopened = new SQLiteDBConnector(file.getFilePath())) {
                reopened.connect();
                assertThat(new ChatFilterEntityStore(reopened).read().keywords()).extracting(ChatKeywordFilterEntity::keyword)
                        .containsExactly("accepted");
            }
        } finally {
            robot.interact(filters::close);
            helper.close();
        }
    }

    @Test
    void cancellingRunningObservationAndClosingCannotInterruptTheCommit(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var store = spy(new ChatFilterEntityStore(db));
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(call -> {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return call.callRealMethod();
            }).when(store).save(any(), any());
            var queue = new LinkedBlockingQueue<Runnable>();
            var settled = new AtomicReference<CompletionStage<Void>>();
            var filters = new ChatFilters(store, queue::add, settled::set, failure -> { throw new AssertionError(failure); });
            var observation = new AtomicReference<FXTask<ChatFilterEntityStore.Snapshot>>();
            try {
                robot.interact(() -> observation.set(filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("accepted"))));
                var running = worker.submit(queue.remove());
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    assertThat(observation.get().cancel(true)).isTrue();
                    filters.close();
                    assertThat(settled.get().toCompletableFuture().isDone()).isFalse();
                });
                release.countDown();
                running.get(5, TimeUnit.SECONDS);

                settled.get().toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(store.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("accepted");
                robot.interact(() -> {
                    assertThat(observation.get().isCancelled()).isTrue();
                    assertThat(filters.getKeywordEntries()).isEmpty();
                });
            } finally {
                release.countDown();
                robot.interact(filters::close);
            }
        }
    }

    @Test
    void retiredWritesAndANewSessionKeepIndependentOutcomes(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            db.execute("""
                    CREATE TRIGGER fail_filter BEFORE INSERT ON chat_keyword_filter WHEN NEW.keyword = 'rejected'
                    BEGIN SELECT RAISE(ABORT, 'injected filter failure'); END;
                    """);
            db.commit();
            var store = new ChatFilterEntityStore(db);
            var queue = new LinkedBlockingQueue<Runnable>();
            var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
            var errors = new CopyOnWriteArrayList<Throwable>();
            var filters = new ChatFilters(store, queue::add, stages::add, errors::add);
            try {
                robot.interact(() -> {
                    filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("rejected")).cancel();
                    filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("old accepted")).cancel();
                    filters.onLogout();
                    filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("new accepted"));
                });
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                robot.interact(() -> assertThat(filters.getKeywordEntries()).isEmpty());
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);

                assertThat(stages).hasSize(3);
                assertThatThrownBy(() -> stages.get(0).toCompletableFuture().join())
                        .hasRootCauseMessage("[SQLITE_CONSTRAINT_TRIGGER] A RAISE function within a trigger fired, causing the SQL statement to abort (injected filter failure)");
                stages.get(1).toCompletableFuture().get(5, TimeUnit.SECONDS);
                stages.get(2).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertThat(store.read().keywords()).extracting(ChatKeywordFilterEntity::keyword)
                        .containsExactly("old accepted", "new accepted");
                robot.interact(() -> {
                    assertThat(errors).isEmpty();
                    assertThat(filters.getKeywordEntries()).extracting(KeywordFilterEntry::getKeyword)
                            .containsExactly("old accepted", "new accepted");
                });
            } finally { robot.interact(filters::close); }
        }
    }

    @Test
    void aCommittedSnapshotApplicationFailureReachesTheOwnerAndObservation(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            db.execute("INSERT INTO chat_keyword_filter(type, keyword) VALUES(99, 'legacy-invalid');");
            db.commit();
            var store = new ChatFilterEntityStore(db);
            var queue = new LinkedBlockingQueue<Runnable>();
            var errors = new CopyOnWriteArrayList<Throwable>();
            var uncaught = new CopyOnWriteArrayList<Throwable>();
            var previous = new AtomicReference<Thread.UncaughtExceptionHandler>();
            var observation = new AtomicReference<FXTask<ChatFilterEntityStore.Snapshot>>();
            var filters = new ChatFilters(store, queue::add, ignored -> {}, errors::add);
            try {
                robot.interact(() -> {
                    previous.set(Thread.currentThread().getUncaughtExceptionHandler());
                    Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
                    observation.set(filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("accepted")));
                });
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);

                robot.interact(() -> {
                    assertThat(errors).singleElement().satisfies(error ->
                            assertThat(error).isInstanceOf(IllegalArgumentException.class).hasMessage("unknown type = 99"));
                    assertThat(uncaught).isEmpty();
                    assertThat(filters.getKeywordEntries()).isEmpty();
                    assertThat(observation.get().isDone()).isTrue();
                });
                assertThatThrownBy(() -> observation.get().get(5, TimeUnit.SECONDS)).hasCause(errors.getFirst());
                assertThat(store.read().keywords()).extracting(ChatKeywordFilterEntity::keyword)
                        .containsExactly("legacy-invalid", "accepted");
            } finally {
                robot.interact(() -> {
                    Thread.currentThread().setUncaughtExceptionHandler(previous.get());
                    filters.close();
                });
            }
        }
    }

    @Test
    void logoutRetainsAnUndeliveredFailureWithoutWaitingForFxEvents(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            db.execute("""
                    CREATE TRIGGER fail_filter BEFORE INSERT ON chat_keyword_filter
                    BEGIN SELECT RAISE(ABORT, 'injected filter failure'); END;
                    """);
            db.commit();
            var store = new ChatFilterEntityStore(db);
            var settled = new AtomicReference<CompletionStage<Void>>();
            var finished = new CountDownLatch(1);
            var errors = new CopyOnWriteArrayList<Throwable>();
            var filters = new ChatFilters(store, command -> worker.execute(() -> {
                try { command.run(); }
                finally { finished.countDown(); }
            }), settled::set, errors::add);
            try {
                robot.interact(() -> {
                    filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("accepted"));
                    try { assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }

                    filters.onLogout();

                    assertThat(settled.get().toCompletableFuture().isCompletedExceptionally()).isTrue();
                    assertThat(filters.getKeywordEntries()).isEmpty();
                    assertThat(filters.getUserEntries()).isEmpty();
                });
                assertThatThrownBy(() -> settled.get().toCompletableFuture().join())
                        .hasRootCauseMessage("[SQLITE_CONSTRAINT_TRIGGER] A RAISE function within a trigger fired, causing the SQL statement to abort (injected filter failure)");
                robot.interact(() -> assertThat(errors).isEmpty());
                assertThat(store.read().keywords()).isEmpty();
            } finally { robot.interact(filters::close); }
        }
    }

    @Test
    void applicationShutdownRetainsSqlFailureAfterFiltersClose(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = spy(new TaskWorker());
        var executor = worker.getExecutor();
        Executor gated = command -> executor.execute(() -> {
            entered.countDown();
            try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            command.run();
        });
        doReturn(gated).when(worker).getExecutor();
        var publishers = mock(EventPublishers.class);
        var errors = new CopyOnWriteArrayList<Throwable>();
        var helper = new AppHelper(worker, publishers, errors::add);
        var file = new UserDataFile(directory.resolve("filters.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        file.getConnector().execute("""
                CREATE TRIGGER fail_filter BEFORE INSERT ON chat_keyword_filter
                BEGIN SELECT RAISE(ABORT, 'injected filter failure'); END;
                """);
        file.getConnector().commit();
        helper.setUserDataFile(file);
        var filters = helper.getChatFilters();
        var failure = new AtomicReference<Throwable>();
        try {
            robot.interact(() -> filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("accepted")));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                helper.prepareShutdown();
                release.countDown();
                failure.set(catchThrowable(helper::close));
                assertThat(failure.get()).isNotNull().hasRootCauseMessage(
                        "[SQLITE_CONSTRAINT_TRIGGER] A RAISE function within a trigger fired, causing the SQL statement to abort (injected filter failure)");
            });
            assertThat(errors).isEmpty();
            assertThat(file.getConnector().isConnected()).isFalse();
            verify(worker).close();
            verify(publishers).close();
            try (var reopened = new SQLiteDBConnector(file.getFilePath())) {
                reopened.connect();
                var saved = new ChatFilterEntityStore(reopened).read();
                assertThat(saved.keywords()).isEmpty();
                assertThat(saved.users()).isEmpty();
            }
        } finally {
            release.countDown();
            robot.interact(filters::close);
            var repeated = catchThrowable(helper::close);
            if (repeated != null) assertThat(repeated).isSameAs(failure.get());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writes")
    void cancellingQueuedObservationPreservesAcceptedWrite(String operation, List<String> keywords,
                                                          List<ChatUserFilterEntity> users, FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var store = new ChatFilterEntityStore(db);
            var initial = store.save(new ChatFilterEntityStore.Changes(
                    List.of(new ChatKeywordFilterEntity(0, 2, "old")), Set.of(),
                    List.of(new ChatUserFilterEntity("42", "Old", "old comment")), Set.of()), () -> {});
            var queue = new LinkedBlockingQueue<Runnable>();
            var filters = new ChatFilters(store, queue::add, ignored -> {}, failure -> { throw new AssertionError(failure); });
            var observation = new AtomicReference<FXTask<ChatFilterEntityStore.Snapshot>>();
            try {
                robot.interact(() -> {
                    filters.applySnapshot(ChatFilters.Snapshot.from(initial));
                    observation.set(switch (operation) {
                        case "save keyword" -> filters.saveKeywordFilter(KeywordFilterEntry.exactMatch("new"));
                        case "delete keyword" -> filters.deleteKeywordFilter(filters.getKeywordEntries().getFirst());
                        case "save user" -> filters.saveUserFilter(new UserFilterEntry("84", "New", "new comment"));
                        case "delete user" -> filters.deleteUserFilter(filters.getUserEntries().getFirst());
                        default -> throw new AssertionError(operation);
                    });
                    assertThat(observation.get().cancel()).isTrue();
                });

                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                var saved = store.read();
                assertThat(saved.keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactlyElementsOf(keywords);
                assertThat(saved.keywords()).allSatisfy(row -> {
                    assertThat(row.id()).isPositive();
                    assertThat(row.type()).isEqualTo(2);
                });
                assertThat(saved.users()).containsExactlyInAnyOrderElementsOf(users);
                assertThat(saved.revision()).isEqualTo(2);
                robot.interact(() -> {
                    assertThat(filters.getKeywordEntries()).extracting(KeywordFilterEntry::getKeyword).containsExactlyElementsOf(keywords);
                    assertThat(filters.getUserEntries().stream().map(entry -> new ChatUserFilterEntity(
                            entry.getUserId(), entry.getUserName(), entry.getComment())).toList())
                            .containsExactlyInAnyOrderElementsOf(users);
                    assertThat(observation.get().isCancelled()).isTrue();
                });
            } finally {
                robot.interact(filters::close);
            }
        }
    }

    private static Stream<Arguments> writes() {
        var oldUser = new ChatUserFilterEntity("42", "Old", "old comment");
        var newUser = new ChatUserFilterEntity("84", "New", "new comment");
        return Stream.of(
                Arguments.of("save keyword", List.of("old", "new"), List.of(oldUser)),
                Arguments.of("delete keyword", List.of(), List.of(oldUser)),
                Arguments.of("save user", List.of("old"), List.of(oldUser, newUser)),
                Arguments.of("delete user", List.of("old"), List.of()));
    }
}
