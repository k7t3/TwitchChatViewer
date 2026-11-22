package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PreferencesSaveTest {
    private static final SettingsDraft DRAFT = new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty());
    private static final SettingsSaveService.SavedSettings SAVED = new SettingsSaveService.SavedSettings(DRAFT,
            new ChatFilterEntityStore.Snapshot(java.util.List.of(), java.util.List.of(), 1));

    @Test
    void anAcceptedSaveRemainsOnItsOwnedExecutorWhenTheWindowCloses(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var saves = mock(SettingsSaveService.class);
        when(saves.save(DRAFT)).thenReturn(SAVED);
        doAnswer(invocation -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return SAVED;
        }).when(saves).save(DRAFT);
        var reference = new AtomicReference<PreferencesViewModel>();
        var applied = new CountDownLatch(1);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> {
                    var model = new PreferencesViewModel(saves, worker, ignored -> {}, failure -> { throw new AssertionError(failure); });
                    reference.set(model);
                    model.saveAsync(DRAFT, saved -> { assertThat(Platform.isFxApplicationThread()).isTrue(); applied.countDown(); });
                    assertThat(model.isBusy()).isTrue();
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    reference.get().close();
                    assertThat(reference.get().isBusy()).isFalse();
                    assertThat(reference.get().isSavePending()).isTrue();
                    assertThatThrownBy(() -> reference.get().saveAsync(DRAFT, saved -> {})).isInstanceOf(IllegalStateException.class);
                });
                release.countDown();
            } finally { release.countDown(); }
        } finally { if (reference.get() != null) robot.interact(reference.get()::close); }
        assertThat(applied.await(5, TimeUnit.SECONDS)).isTrue();
        robot.interact(() -> assertThat(reference.get().isSavePending()).isFalse());
        verify(saves).save(DRAFT);
    }

    @Test
    void aQueuedSavePreventsDuplicateSubmissionAndFailureAllowsRetry(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var saves = mock(SettingsSaveService.class);
        when(saves.save(DRAFT)).thenReturn(SAVED);
        var failure = new IllegalStateException("preferences could not flush");
        when(saves.save(DRAFT)).thenThrow(failure).thenReturn(SAVED);
        var errors = new CopyOnWriteArrayList<Throwable>();
        var model = new PreferencesViewModel(saves, queue::add, ignored -> {}, errors::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.saveAsync(DRAFT, saved -> {});
                assertThatThrownBy(() -> model.saveAsync(DRAFT, saved -> {})).isInstanceOf(IllegalStateException.class);
                assertThat(queue).hasSize(1);
            });
            worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(model.isBusy()).isFalse();
                assertThat(errors).containsExactly(failure);
                model.saveAsync(DRAFT, saved -> {});
            });
            worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(model.isBusy()).isFalse());
            verify(saves, times(2)).save(DRAFT);
        } finally { robot.interact(model::close); }
    }

    @Test
    void rejectedSubmissionDoesNotFlushAndResetsBusy(FxRobot robot) {
        var saves = mock(SettingsSaveService.class);
        when(saves.save(DRAFT)).thenReturn(SAVED);
        var failure = new RejectedExecutionException("worker is stopping");
        var model = new PreferencesViewModel(saves, task -> { throw failure; }, ignored -> {}, error -> { throw new AssertionError(error); });
        try {
            robot.interact(() -> {
                assertThatThrownBy(() -> model.saveAsync(DRAFT, saved -> {})).isSameAs(failure);
                assertThat(model.isBusy()).isFalse();
                verify(saves, never()).save(DRAFT);
            });
        } finally { robot.interact(model::close); }
    }
}
