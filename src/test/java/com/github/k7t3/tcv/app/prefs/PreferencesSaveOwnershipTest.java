package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PreferencesSaveOwnershipTest {
    @ParameterizedTest(name = "submission rejected during {0}")
    @ValueSource(strings = {"registration", "execution"})
    void aRejectedSaveResetsTheEditorAndLeavesNoPendingIo(String boundary, FxRobot robot) {
        var saves = mock(SettingsSaveService.class);
        var failure = new RejectedExecutionException("application is stopping");
        var draft = new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty());
        var actual = new AtomicReference<CompletionStage<Void>>();
        var model = new PreferencesViewModel(saves, command -> { throw failure; }, stage -> {
            actual.set(stage);
            if (boundary.equals("registration")) throw failure;
        }, unexpected -> { throw new AssertionError(unexpected); });
        try {
            robot.interact(() -> {
                assertThatThrownBy(() -> model.saveAsync(draft, saved -> { throw new AssertionError("save was rejected"); }))
                        .isSameAs(failure);
                assertThat(model.isBusy()).isFalse();
                assertThat(model.isSavePending()).isFalse();
            });
            assertThat(actual.get().toCompletableFuture().isDone()).isTrue();
            assertThatCode(() -> actual.get().toCompletableFuture().join()).doesNotThrowAnyException();
            verify(saves, never()).save(draft);
        } finally { robot.interact(model::close); }
    }

    @Test
    void closingBeforeFailureDeliverySettlesTheOutcomeWithoutFxEventDelivery(FxRobot robot) throws Exception {
        var saves = mock(SettingsSaveService.class);
        var failure = new IllegalStateException("save could not commit");
        var draft = new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty());
        when(saves.save(draft)).thenThrow(failure);
        var actual = new AtomicReference<CompletionStage<Void>>();
        var finished = new CountDownLatch(1);
        var errors = new CopyOnWriteArrayList<Throwable>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var model = new PreferencesViewModel(saves, command -> worker.execute(() -> {
                try { command.run(); }
                finally { finished.countDown(); }
            }), actual::set, errors::add);
            try {
                robot.interact(() -> {
                    model.saveAsync(draft, saved -> { throw new AssertionError("failed save must not apply"); });
                    try { assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }

                    model.close();

                    assertThat(actual.get().toCompletableFuture().isCompletedExceptionally()).isTrue();
                });
                assertThatThrownBy(() -> actual.get().toCompletableFuture().join()).hasCause(failure);
                robot.interact(() -> assertThat(errors).isEmpty());
            } finally { robot.interact(model::close); }
        }
    }

    @Test
    void cancellingAnInFlightSaveAndClosingTheEditorDoesNotInterruptItsCommit(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var preferences = new AppPreferences(node);
        var draft = new SettingsDraft(Map.of("theme", Optional.of("NORD")), new ChatFilterEntityStore.Changes(
                List.of(new ChatKeywordFilterEntity(0, 0, "blocked")), Set.of(), List.of(), Set.of()));
        var observation = new AtomicReference<FXTask<?>>();
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var saves = spy(new SettingsSaveService(preferences, filters));
            doAnswer(call -> {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return call.callRealMethod();
            }).when(saves).save(draft);
            var model = new PreferencesViewModel(saves, queue::add, ignored -> {}, unexpected -> { throw new AssertionError(unexpected); });
            try {
                robot.interact(() -> observation.set(model.saveAsync(draft, saved -> preferences.applyCommittedValues(saved.draft().values()))));
                var running = worker.submit(queue.remove());
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    observation.get().cancel(true);
                    model.close();
                    assertThat(model.isSavePending()).isTrue();
                });
                release.countDown();
                running.get(5, TimeUnit.SECONDS);

                assertThat(node.get("theme", null)).isEqualTo("NORD");
                assertThat(filters.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
                robot.interact(() -> {
                    assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.NORD);
                    assertThat(model.isSavePending()).isFalse();
                    assertThat(observation.get().isCancelled()).isTrue();
                });
            } finally {
                release.countDown();
                robot.interact(model::close);
            }
        }
    }

    @Test
    void aFailedApplicationSettlesTheSaveAndReportsItsCauseWithoutAnUncaughtUiFailure(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var saves = mock(SettingsSaveService.class);
        var draft = new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty());
        var saved = new SettingsSaveService.SavedSettings(draft, new ChatFilterEntityStore.Snapshot(List.of(), List.of(), 1));
        when(saves.save(draft)).thenReturn(saved);
        var errors = new CopyOnWriteArrayList<Throwable>();
        var uncaught = new CopyOnWriteArrayList<Throwable>();
        var failure = new IllegalStateException("saved settings could not be applied");
        var previous = new AtomicReference<Thread.UncaughtExceptionHandler>();
        var observation = new AtomicReference<FXTask<?>>();
        var model = new PreferencesViewModel(saves, queue::add, ignored -> {}, errors::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                var thread = Thread.currentThread();
                previous.set(thread.getUncaughtExceptionHandler());
                thread.setUncaughtExceptionHandler((source, error) -> uncaught.add(error));
                observation.set(model.saveAsync(draft, value -> { throw failure; }));
            });
            worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);

            robot.interact(() -> {
                assertThat(errors).containsExactly(failure);
                assertThat(uncaught).isEmpty();
                assertThat(model.isBusy()).isFalse();
                assertThat(model.isSavePending()).isFalse();
                assertThat(observation.get().isDone()).isTrue();
            });
            assertThatThrownBy(() -> observation.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            verify(saves).save(draft);
        } finally {
            robot.interact(() -> {
                Thread.currentThread().setUncaughtExceptionHandler(previous.get());
                model.close();
            });
        }
    }

    @Test
    void aSaveFailureAfterTheEditorClosesIsRetainedByApplicationShutdown(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(new TaskWorker(), publishers, unexpected -> { throw new AssertionError(unexpected); });
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var preferences = spy(new AppPreferences(node));
        var failure = new IllegalStateException("preferences could not flush");
        doThrow(failure).when(preferences).save();
        var draft = new SettingsDraft(Map.of("theme", Optional.of("NORD")), new ChatFilterEntityStore.Changes(
                List.of(new ChatKeywordFilterEntity(0, 0, "blocked")), Set.of(), List.of(), Set.of()));
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var saves = spy(new SettingsSaveService(preferences, filters));
            doAnswer(call -> {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return call.callRealMethod();
            }).when(saves).save(draft);
            var model = new PreferencesViewModel(saves, helper.getExecutor(), helper::awaitOnShutdown,
                    unexpected -> { throw new AssertionError(unexpected); });
            try {
                robot.interact(() -> model.saveAsync(draft, saved -> { throw new AssertionError("failed save must not apply"); }));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                release.countDown();

                assertThatThrownBy(helper::close).isSameAs(failure);

                assertThat(node.get("theme", null)).isEqualTo("PRIMER");
                assertThat(filters.read().keywords()).isEmpty();
                verify(publishers).close();
            } finally {
                release.countDown();
                robot.interact(model::close);
                try { helper.close(); }
                catch (RuntimeException error) { if (error != failure) throw error; }
            }
        }
    }

    @Test
    void cancellingTheObserverCannotDiscardTheAcceptedSaveOrItsApplication(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var preferences = new AppPreferences(node);
        var draft = new SettingsDraft(Map.of("theme", Optional.of("NORD")), new ChatFilterEntityStore.Changes(
                List.of(new ChatKeywordFilterEntity(0, 0, "blocked")), Set.of(), List.of(), Set.of()));
        var observation = new AtomicReference<FXTask<?>>();
        var applied = new AtomicReference<SettingsSaveService.SavedSettings>();
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var model = new PreferencesViewModel(new SettingsSaveService(preferences, filters), queue::add, ignored -> {},
                    failure -> { throw new AssertionError(failure); });
            try {
                robot.interact(() -> {
                    observation.set(model.saveAsync(draft, saved -> {
                        preferences.applyCommittedValues(saved.draft().values());
                        applied.set(saved);
                    }));
                    observation.get().cancel(true);
                });

                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);

                assertThat(node.get("theme", null)).isEqualTo("NORD");
                assertThat(filters.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
                robot.interact(() -> {
                    assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.NORD);
                    assertThat(applied.get()).isNotNull();
                    assertThat(applied.get().draft()).isEqualTo(draft);
                    assertThat(model.isSavePending()).isFalse();
                    assertThat(observation.get().isCancelled()).isTrue();
                });
            } finally { robot.interact(model::close); }
        }
    }
}
