package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.TaskWorker;
import org.junit.jupiter.api.AutoClose;
import com.github.k7t3.tcv.app.image.ImageLoader;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import com.github.k7t3.tcv.app.service.FXTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(ApplicationExtension.class)
class ChannelReleaseTest {
    @AutoClose
    private final TaskWorker worker = new TaskWorker();


    @Test
    void anOldReleaseFinishingAfterReloginKeepsTheNewSessionsModel(FxRobot robot) throws Exception {
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var oldRepository = mock(ChannelRepository.class);
        var newRepository = mock(ChannelRepository.class);
        var owner = new Broadcaster("123", "channel", "Channel", "", "");
        var oldChannel = mock(TwitchChannel.class);
        var newChannel = mock(TwitchChannel.class);
        when(oldChannel.getBroadcaster()).thenReturn(owner);
        when(newChannel.getBroadcaster()).thenReturn(owner);
        when(oldRepository.getOrLoadChannel(owner)).thenReturn(oldChannel);
        when(newRepository.getOrLoadChannel(owner)).thenReturn(newChannel);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        }).when(oldRepository).releaseChannel(oldChannel);
        var request = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.setRepository(oldRepository); request.set(model.getChannelAsync(owner)); });
            var oldModel = request.get().get(5, TimeUnit.SECONDS);
            var releasing = worker.submit(() -> model.releaseChannel(oldModel));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    model.onLogout();
                    model.setRepository(newRepository);
                    request.set(model.getChannelAsync(owner));
                });
                var newModel = request.get().get(5, TimeUnit.SECONDS);
                release.countDown();
                releasing.get(5, TimeUnit.SECONDS);
                WaitForAsyncUtils.waitForFxEvents();
                robot.interact(() -> assertThat(model.getChannels()).containsExactly(newModel));
                verify(newRepository, never()).releaseChannel(newChannel);
            } finally {
                release.countDown();
                releasing.get(5, TimeUnit.SECONDS);
            }
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void lateReleaseAfterLogoutDoesNotAccessEndedRepository(FxRobot robot) {
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var repository = mock(ChannelRepository.class);
        var channel = mock(TwitchChannel.class);
        var item = mock(TwitchChannelViewModel.class);
        when(item.getChannel()).thenReturn(channel);
        robot.interact(() -> {
            model.setRepository(repository);
            model.onLogout();
        });
        try {
            assertThatCode(() -> model.releaseChannel(item)).doesNotThrowAnyException();

            verify(repository, never()).clear();
            verify(repository, never()).releaseChannel(channel);
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void releaseRemainsPartOfCallingTaskUntilDomainReleaseCompletes(FxRobot robot) throws Exception {
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), worker.getExecutor(), ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var repository = mock(ChannelRepository.class);
        var channel = mock(TwitchChannel.class);
        var broadcaster = new Broadcaster("123", "channel", "Channel", "", "");
        when(channel.getBroadcaster()).thenReturn(broadcaster);
        when(repository.getOrLoadChannel(broadcaster)).thenReturn(channel);
        var loaded = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        }).when(repository).releaseChannel(channel);
        robot.interact(() -> {
            model.setRepository(repository);
            loaded.set(model.getChannelAsync(broadcaster));
        });
        var item = loaded.get().get(5, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var caller = executor.submit(() -> model.releaseChannel(item));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> caller.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                release.countDown();
                caller.get(5, TimeUnit.SECONDS);
                robot.interact(() -> assertThat(model.getChannels()).isEmpty());
            } finally {
                release.countDown();
                caller.get(5, TimeUnit.SECONDS);
            }
        } finally {
            robot.interact(model::close);
        }
    }
}
