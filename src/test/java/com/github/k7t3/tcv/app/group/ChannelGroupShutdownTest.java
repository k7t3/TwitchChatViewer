package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupShutdownTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void stoppingOnFxDrainsAcceptedWritesBeforeClosingTheRealDatabase(boolean failFirst, boolean cancelledRead, FxRobot robot) throws Exception {
        var file = new UserDataFile(directory.resolve("groups.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        if (failFirst) file.getConnector().execute("""
                CREATE TRIGGER fail_first_group BEFORE INSERT ON groups
                WHEN NEW.name = 'first'
                BEGIN SELECT RAISE(ABORT, 'injected group failure'); END;
                """);
        if (failFirst) file.getConnector().commit();
        var worker = spy(new TaskWorker());
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        helper.setUserDataFile(file);
        var service = spy(new ChannelGroupEntityService(file.getConnector()));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var secondRelease = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var failed = new AtomicReference<Throwable>();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release SQL write");
            } else {
                secondEntered.countDown();
                if (!secondRelease.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release second SQL write");
            }
            try { return invocation.callRealMethod(); }
            catch (Throwable error) { failed.compareAndSet(null, error); throw error; }
        }).when(service).save(any(), any());
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        var parked = new LinkedBlockingQueue<Runnable>();
        java.util.concurrent.Executor executor = cancelledRead ? parked::add : helper.getExecutor();
        var repository = new ChannelGroupRepository(service, executor, helper::awaitOnShutdown, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var group = new ChannelGroup();
        var latest = new AtomicReference<FXTask<?>>();
        try (var coordinator = Executors.newSingleThreadExecutor()) {
            try {
                robot.interact(() -> { group.setName("first"); repository.saveAsync(group); });
                var reading = FXTask.task(repository::loadSnapshot);
                if (cancelledRead) helper.getExecutor().execute(reading);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    group.setName("last"); latest.set(repository.saveAsync(group));
                    if (cancelledRead) reading.cancel(true);
                    repository.close();
                });
                var unblock = coordinator.submit(() -> {
                    assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(file.getConnector().isConnected()).isTrue();
                    release.countDown();
                    assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(file.getConnector().isConnected()).isTrue();
                    verify(publishers, never()).close();
                    secondRelease.countDown(); return null;
                });
                robot.interact(() -> {
                    if (failFirst) assertThatThrownBy(helper::close).satisfies(error -> assertThat(error).isSameAs(failed.get()));
                    else assertThatCode(helper::close).doesNotThrowAnyException();
                });
                unblock.get(5, TimeUnit.SECONDS);
                latest.get().get(5, TimeUnit.SECONDS);
                assertThat(file.getConnector().isConnected()).isFalse();
                verify(publishers).close();
                try (var reopened = new SQLiteDBConnector(file.getFilePath())) {
                    reopened.connect();
                    var stored = new ChannelGroupEntityService(reopened).retrieveAll();
                    assertThat(stored).hasSize(1);
                    assertThat(stored.getFirst().name()).isEqualTo("last");
                }
                robot.interact(() -> {
                    assertThat(repository.getAll()).isEmpty();
                    assertThat(group.getId()).isNull();
                });
            } finally {
                release.countDown();
                secondRelease.countDown();
                robot.interact(repository::close);
                try { helper.close(); }
                catch (RuntimeException | Error expected) { assertThat(expected).isSameAs(failed.get()); }
            }
        }
    }
}
