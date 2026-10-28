package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelLoadingQueueTest {
    private static final Broadcaster OWNER = new Broadcaster("123", "channel", "Channel", "", "");

    @Test
    void duplicateRequestsShareOneModelAndOneWorkerRequest(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var domain = mock(ChannelRepository.class);
        var channel = channel();
        when(domain.getOrLoadChannel(OWNER)).thenReturn(channel);
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), queue::add, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var request = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> {
                model.setRepository(domain);
                request.set(model.getChannelAsync(OWNER));
                assertThat(model.getChannelAsync(OWNER)).isSameAs(request.get());
                assertThat(queue).hasSize(1);
                assertThat(model.getChannels()).isEmpty();
            });
            runNext(queue);
            var loaded = request.get().get(5, TimeUnit.SECONDS);
            var cached = new AtomicReference<FXTask<TwitchChannelViewModel>>();
            robot.interact(() -> {
                assertThat(model.getChannels()).containsExactly(loaded);
                cached.set(model.getChannelAsync(OWNER));
                assertThat(queue).isEmpty();
            });
            assertThat(cached.get().get(5, TimeUnit.SECONDS)).isSameAs(loaded);
            verify(domain).getOrLoadChannel(OWNER);
        } finally {
            robot.interact(model::close);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failuresAreReturnedToTheCallerAndDoNotPreventRetry(boolean reject, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var domain = mock(ChannelRepository.class);
        var channel = channel();
        var failure = reject ? new RejectedExecutionException("worker rejected") : new IllegalStateException("fetch failed");
        when(domain.getOrLoadChannel(OWNER)).thenThrow(failure).thenReturn(channel);
        var rejectOnce = new java.util.concurrent.atomic.AtomicBoolean(reject);
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), action -> {
            if (rejectOnce.getAndSet(false)) throw (RejectedExecutionException) failure;
            queue.add(action);
        }, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var request = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> { model.setRepository(domain); request.set(model.getChannelAsync(OWNER)); });
            if (!reject) runNext(queue);
            assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCause(failure);
            if (reject) doReturn(channel).when(domain).getOrLoadChannel(OWNER);

            robot.interact(() -> {
                assertThat(model.getChannels()).isEmpty();
                request.set(model.getChannelAsync(OWNER));
            });
            runNext(queue);
            var loaded = request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(model.getChannels()).containsExactly(loaded));
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void cancellingAQueuedRequestPreventsDomainAccessAndAllowsRetry(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var domain = mock(ChannelRepository.class);
        var channel = channel();
        when(domain.getOrLoadChannel(OWNER)).thenReturn(channel);
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), queue::add, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var request = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> {
                model.setRepository(domain);
                request.set(model.getChannelAsync(OWNER));
                request.get().cancel();
            });
            runNext(queue);
            verify(domain, never()).getOrLoadChannel(any());

            robot.interact(() -> request.set(model.getChannelAsync(OWNER)));
            runNext(queue);
            assertThat(request.get().get(5, TimeUnit.SECONDS)).isNotNull();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void initialLoadsCoalesceAndRepeatedSnapshotsReplaceGroups(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var domain = mock(ChannelRepository.class);
        var channel = channel();
        when(domain.getOrLoadChannels(any())).thenReturn(List.of());
        when(domain.getOrLoadChannels()).thenReturn(List.of(channel));
        var service = mock(ChannelGroupEntityService.class);
        var id = UUID.randomUUID();
        var now = LocalDateTime.now();
        when(service.retrieveAll()).thenReturn(
                List.of(new ChannelGroupEntity(id, "First", "", false, now, now, Set.of("123")))).thenReturn(
                List.of(new ChannelGroupEntity(id, "Second", "", false, now, now, Set.of("123"))));
        var groups = new ChannelGroupRepository(service, Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var model = new ChannelViewModelRepository(groups, mock(ImageLoader.class), queue::add, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var request = new AtomicReference<FXTask<?>>();
        try {
            robot.interact(() -> {
                model.setRepository(domain);
                request.set(model.loadAllAsync());
                assertThat(model.loadAllAsync()).isSameAs(request.get());
                assertThat(queue).hasSize(1);
            });
            runNext(queue);
            request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> request.set(model.loadAllAsync()));
            runNext(queue);
            request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(model.getChannels()).hasSize(1);
                assertThat(groups.getAll()).hasSize(1);
                assertThat(groups.getAll().getFirst().getName()).isEqualTo("Second");
                assertThat(groups.getAll().getFirst().getChannels()).containsExactly(model.getChannels().getFirst());
            });
            verify(service, times(2)).retrieveAll();
        } finally {
            robot.interact(model::close);
            robot.interact(groups::close);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closingAfterFetchButBeforeFxAdoptionDiscardsThePendingData(boolean initial, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var domain = mock(ChannelRepository.class);
        var channel = channel();
        when(domain.getOrLoadChannel(OWNER)).thenReturn(channel);
        when(domain.getOrLoadChannels(any())).thenReturn(List.of());
        when(domain.getOrLoadChannels()).thenReturn(List.of(channel));
        var service = mock(ChannelGroupEntityService.class);
        when(service.retrieveAll()).thenReturn(List.of());
        var groups = new ChannelGroupRepository(service, Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var model = new ChannelViewModelRepository(groups, mock(ImageLoader.class), queue::add, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var request = new AtomicReference<FXTask<?>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.setRepository(domain);
                request.set(initial ? model.loadAllAsync() : model.getChannelAsync(OWNER));
                try {
                    worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                model.close();
            });
            robot.interact(() -> {
                assertThat(request.get().isCancelled()).isTrue();
                assertThat(model.getChannels()).isEmpty();
                assertThat(groups.getAll()).isEmpty();
            });
            verify(channel, never()).isFollowing();
        } finally {
            robot.interact(model::close);
            robot.interact(groups::close);
        }
    }

    @Test
    void aClosedRepositoryCannotStartNewLoadsOrAcceptAnotherSession(FxRobot robot) {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), queue::add, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        robot.interact(() -> {
            model.close();
            assertThatThrownBy(() -> model.getChannelAsync(OWNER)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(model::loadAllAsync).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> model.setRepository(mock(ChannelRepository.class))).isInstanceOf(IllegalStateException.class);
            assertThat(queue).isEmpty();
        });
    }

    private static TwitchChannel channel() {
        var channel = mock(TwitchChannel.class);
        when(channel.getBroadcaster()).thenReturn(OWNER);
        return channel;
    }

    private static void runNext(LinkedBlockingQueue<Runnable> queue) throws InterruptedException {
        var action = queue.poll(5, TimeUnit.SECONDS);
        assertThat(action).isNotNull();
        action.run();
    }
}
