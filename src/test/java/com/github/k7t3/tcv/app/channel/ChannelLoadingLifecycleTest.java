package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.TaskWorker;
import org.junit.jupiter.api.AutoClose;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelLoadingLifecycleTest {
    @AutoClose
    private final TaskWorker worker = new TaskWorker();

    private static final Broadcaster OWNER = new Broadcaster("123", "channel", "Channel", "profile", "offline");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingSessionCancelsTheLoadAndDiscardsALateResult(boolean logout, FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var domain = mock(ChannelRepository.class);
        var channel = channel(new AtomicBoolean());
        when(domain.getOrLoadChannel(OWNER)).thenAnswer(invocation -> {
            entered.countDown();
            awaitUninterruptibly(release);
            finished.countDown();
            return channel;
        });
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var task = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        var delivered = new AtomicInteger();
        try {
            robot.interact(() -> {
                model.setRepository(domain);
                task.set(model.getChannelAsync(OWNER).onDone(value -> delivered.incrementAndGet()));
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> { if (logout) model.onLogout(); else model.close(); });
            release.countDown();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            WaitForAsyncUtils.waitForFxEvents();
            robot.interact(() -> {
                assertThat(task.get().isCancelled()).isTrue();
                assertThat(model.getChannels()).isEmpty();
                assertThat(delivered).hasValue(0);
            });
        } finally {
            release.countDown();
            robot.interact(model::close);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void endingTheSessionDuringTheFirstPublicationCannotAdoptMoreOfItsSnapshot(boolean logout, FxRobot robot) {
        var queue = new java.util.ArrayDeque<Runnable>();
        var domain = mock(ChannelRepository.class);
        var first = channel(new AtomicBoolean());
        var second = mock(TwitchChannel.class);
        when(second.getBroadcaster()).thenReturn(new Broadcaster("456", "next", "Next", "next-profile", ""));
        when(domain.getOrLoadChannels(any())).thenReturn(List.of(first, second));
        when(domain.getOrLoadChannels()).thenReturn(List.of());
        var groups = mock(ChannelGroupRepository.class);
        when(groups.loadSnapshot()).thenReturn(List.of());
        var model = new ChannelViewModelRepository(groups, mock(ImageLoader.class), queue::add, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var result = new AtomicReference<FXTask<?>>();
        robot.interact(() -> {
            model.setRepository(domain);
            model.getChannels().addListener((ListChangeListener<TwitchChannelViewModel>) change -> {
                while (change.next()) if (change.wasAdded()) { if (logout) model.onLogout(); else model.close(); }
            });
            result.set(model.loadAllAsync());
        });
        try {
            java.util.Objects.requireNonNull(queue.poll()).run();
            robot.interact(() -> {
                assertThat(model.getChannels()).isEmpty();
                assertThat(result.get().isCancelled()).isTrue();
                verify(groups, never()).applySnapshot(any(), any());
            });
        } finally { robot.interact(model::close); }
    }

    @Test
    void individualChannelModelsAreCreatedOnFx(FxRobot robot) throws Exception {
        var builtOutsideFx = new AtomicBoolean();
        var domain = mock(ChannelRepository.class);
        var loadedChannel = channel(builtOutsideFx);
        when(domain.getOrLoadChannel(OWNER)).thenReturn(loadedChannel);
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var task = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> { model.setRepository(domain); task.set(model.getChannelAsync(OWNER)); });
            task.get().get(5, TimeUnit.SECONDS);
            WaitForAsyncUtils.waitForFxEvents();
            assertThat(builtOutsideFx).isFalse();
            robot.interact(() -> assertThat(model.getChannels()).containsExactly(task.get().getValue()));
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void initialLoadReadsOneSnapshotOnTheWorkerAndAppliesGroupsOnFx(FxRobot robot) throws Exception {
        var builtOutsideFx = new AtomicBoolean();
        var groupChangedOutsideFx = new AtomicBoolean();
        var databaseReadOnFx = new AtomicBoolean();
        var domain = mock(ChannelRepository.class);
        var channel = channel(builtOutsideFx);
        when(domain.getOrLoadChannels(any())).thenReturn(List.of(channel));
        when(domain.getOrLoadChannels()).thenReturn(List.of(channel));
        var service = mock(ChannelGroupEntityService.class);
        var now = LocalDateTime.now();
        var entity = new ChannelGroupEntity(UUID.randomUUID(), "Group", "", false, now, now, Set.of("123"));
        when(service.retrieveAll()).thenAnswer(invocation -> {
            if (Platform.isFxApplicationThread()) databaseReadOnFx.set(true);
            return List.of(entity);
        });
        var groups = new ChannelGroupRepository(service, worker.getExecutor(), ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        groups.getAll().addListener((ListChangeListener<ChannelGroup>) change -> {
            if (!Platform.isFxApplicationThread()) groupChangedOutsideFx.set(true);
        });
        var model = new ChannelViewModelRepository(groups, mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var task = new AtomicReference<FXTask<?>>();
        try {
            robot.interact(() -> { model.setRepository(domain); task.set(model.loadAllAsync()); });
            task.get().get(5, TimeUnit.SECONDS);
            WaitForAsyncUtils.waitForFxEvents();
            assertThat(builtOutsideFx).isFalse();
            assertThat(groupChangedOutsideFx).isFalse();
            assertThat(databaseReadOnFx).isFalse();
            verify(service).retrieveAll();
            robot.interact(() -> {
                assertThat(model.getChannels()).hasSize(1);
                assertThat(groups.getAll()).hasSize(1);
                assertThat(groups.getAll().getFirst().getChannels()).containsExactly(model.getChannels().getFirst());
            });
        } finally {
            robot.interact(model::close);
            robot.interact(groups::close);
        }
    }

    @Test
    void aChannelFromAnEndedSessionCannotReleaseTheNewSessionsChannel(FxRobot robot) throws Exception {
        var oldDomain = mock(ChannelRepository.class);
        var newDomain = mock(ChannelRepository.class);
        var oldChannel = channel(new AtomicBoolean());
        when(oldDomain.getOrLoadChannel(OWNER)).thenReturn(oldChannel);
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var task = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> { model.setRepository(oldDomain); task.set(model.getChannelAsync(OWNER)); });
            var oldModel = task.get().get(5, TimeUnit.SECONDS);
            WaitForAsyncUtils.waitForFxEvents();
            robot.interact(() -> { model.onLogout(); model.setRepository(newDomain); });

            model.releaseChannel(oldModel);

            verify(newDomain, never()).releaseChannel(any());
            verify(oldDomain, never()).releaseChannel(any());
        } finally {
            robot.interact(model::close);
        }
    }

    private static TwitchChannel channel(AtomicBoolean builtOutsideFx) {
        var channel = mock(TwitchChannel.class);
        when(channel.getBroadcaster()).thenReturn(OWNER);
        when(channel.isFollowing()).thenAnswer(invocation -> {
            if (!Platform.isFxApplicationThread()) builtOutsideFx.set(true);
            return false;
        });
        return channel;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
