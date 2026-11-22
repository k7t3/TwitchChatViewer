package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import com.github.k7t3.tcv.view.core.WindowBounds;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class WindowBoundsShutdownTest {
    @TempDir Path directory;

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void stoppingOnFxWaitsForAcceptedBoundsAndPreservesLateSqlFailures(boolean failFirst, boolean cancelledRead, FxRobot robot) throws Exception {
        var file = new UserDataFile(directory.resolve("bounds.db")); file.connectDatabase(DatabaseVersion.EMPTY);
        if (failFirst) {
            file.getConnector().execute("CREATE TRIGGER fail_first_bounds BEFORE INSERT ON windows WHEN NEW.x = 1 BEGIN SELECT RAISE(ABORT, 'injected bounds failure'); END;");
            file.getConnector().commit();
        }
        var worker = spy(new TaskWorker()); var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); }); helper.setUserDataFile(file);
        var entities = spy(new WindowBoundsEntityService(file.getConnector()));
        var firstEntered = new CountDownLatch(1); var firstRelease = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1); var secondRelease = new CountDownLatch(1);
        var draining = new CountDownLatch(1); var writes = new AtomicInteger(); var failure = new AtomicReference<Throwable>();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 1) { firstEntered.countDown(); await(firstRelease); }
            else { secondEntered.countDown(); await(secondRelease); }
            try { return invocation.callRealMethod(); }
            catch (Throwable error) { failure.set(error); throw error; }
        }).when(entities).save(any());
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        var parked = new LinkedBlockingQueue<Runnable>();
        Executor executor = cancelledRead ? parked::add : helper.getExecutor();
        var service = new WindowBoundsService(entities, executor, helper::awaitOnShutdown, ignored -> {});
        var last = new AtomicReference<FXTask<?>>();
        var reading = new AtomicReference<FXTask<?>>();
        try (var coordinator = Executors.newSingleThreadExecutor()) {
            try {
                robot.interact(() -> service.saveBoundsAsync("room", new WindowBounds(1, 2, 360, 640, false)).cancel(true));
                if (cancelledRead) {
                    reading.set(FXTask.task(() -> service.getBounds("room")));
                    helper.getExecutor().execute(reading.get());
                }
                assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    if (cancelledRead) reading.get().cancel(true);
                    last.set(service.saveBoundsAsync("room", new WindowBounds(2, 2, 360, 640, false)));
                    service.close();
                });
                var unblock = coordinator.submit(() -> {
                    assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(file.getConnector().isConnected()).isTrue(); firstRelease.countDown();
                    assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(file.getConnector().isConnected()).isTrue(); verify(publishers, never()).close();
                    secondRelease.countDown(); return null;
                });
                robot.interact(() -> {
                    if (failFirst) assertThatThrownBy(helper::close).satisfies(error -> assertThat(error).isSameAs(failure.get()));
                    else assertThatCode(helper::close).doesNotThrowAnyException();
                });
                unblock.get(5, TimeUnit.SECONDS); last.get().get(5, TimeUnit.SECONDS);
                assertThat(file.getConnector().isConnected()).isFalse(); verify(publishers).close();
                try (var reopened = new SQLiteDBConnector(file.getFilePath())) {
                    reopened.connect();
                    assertThat(new WindowBoundsEntityService(reopened).get("room").x()).isEqualTo(2);
                }
            } finally {
                firstRelease.countDown(); secondRelease.countDown(); robot.interact(service::close);
                try { helper.close(); } catch (RuntimeException | Error expected) { assertThat(expected).isSameAs(failure.get()); }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void applicationPreparationClosesItsCachedBoundsServiceAndCancelsQueuedRestores(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var worker = mock(TaskWorker.class); when(worker.getExecutor()).thenReturn(queue::add);
        var publishers = mock(EventPublishers.class); var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var file = new UserDataFile(directory.resolve("owned-bounds.db")); file.connectDatabase(DatabaseVersion.EMPTY);
        helper.setUserDataFile(file);
        var service = helper.getWindowBoundsService();
        try {
            robot.interact(() -> {
                var restore = service.getBoundsAsync("room");
                helper.prepareShutdown();
                assertThat(restore.isCancelled()).isTrue();
                assertThatThrownBy(() -> service.getBoundsAsync("room")).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> service.saveBoundsAsync("room", new WindowBounds(1, 2, 360, 640, false)))
                        .isInstanceOf(IllegalStateException.class);
            });
            Runnable queued; while ((queued = queue.poll()) != null) queued.run();
            helper.close(); verify(worker).close(); verify(publishers).close();
            assertThat(file.getConnector().isConnected()).isFalse();
        } finally { helper.close(); }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("SQL write was not released");
    }
}
