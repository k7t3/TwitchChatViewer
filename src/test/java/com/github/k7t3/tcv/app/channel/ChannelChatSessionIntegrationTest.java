package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.twitch4j.chat.TwitchChat;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelChatSessionIntegrationTest {
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void stopDuringJoinWaitsForActualDomainLeaveAndRetainsFailuresAfterFxDetaches(
            boolean logout, boolean fail, FxRobot robot) throws Exception {
        var worker = spy(new TaskWorker());
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var twitch = mock(Twitch.class);
        var client = mock(TwitchClients.class, RETURNS_DEEP_STUBS);
        var chat = mock(TwitchChat.class, RETURNS_DEEP_STUBS);
        var api = mock(TwitchAPI.class);
        when(twitch.getClient()).thenReturn(client);
        when(twitch.getChat()).thenReturn(chat);
        when(twitch.getTwitchAPI()).thenReturn(api);
        when(client.getEventManager().onEvent(any(), any())).thenAnswer(invocation -> mock(IEventSubscription.class));
        when(chat.getEventManager().onEvent(any(), any())).thenAnswer(invocation -> mock(IEventSubscription.class));
        when(api.getFollowChannelOwners()).thenReturn(List.of());
        when(api.getStreams(anyList())).thenReturn(List.of());
        when(api.getStream(anyString())).thenReturn(Optional.empty());
        when(api.getChannelBadgeSet(any())).thenReturn(List.of());
        helper.setTwitch(twitch);
        var domain = helper.getChannelRepository();
        domain.loadAllRelatedChannels();
        var repository = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class),
                helper.getExecutor(), helper::awaitOnShutdown, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var owner = new Broadcaster("123", "channel", "Channel", "", "");
        var joining = new CountDownLatch(1);
        var finishJoin = new CountDownLatch(1);
        var leaving = new CountDownLatch(1);
        var finishLeave = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        var failure = new IllegalStateException("SDK leave failed");
        var releaseFailure = new IllegalArgumentException("monitor release failed");
        doAnswer(invocation -> { joining.countDown(); await(finishJoin); return null; }).when(chat).joinChannel("channel");
        doAnswer(invocation -> {
            leaving.countDown(); await(finishLeave);
            if (fail) throw failure;
            return null;
        }).when(chat).leaveChannel("channel");
        if (fail) doThrow(releaseFailure).when(api).disableStreamEventListener(owner);
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        var request = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try (var coordinator = Executors.newSingleThreadExecutor()) {
            try {
                robot.interact(() -> { repository.setRepository(domain); request.set(repository.getChannelAsync(owner)); });
                var model = request.get().get(5, TimeUnit.SECONDS);
                robot.interact(model::joinChatAsync);
                assertThat(joining.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> { if (logout) repository.onLogout(); else repository.close(); });
                var releasing = coordinator.submit(() -> {
                    assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                    finishJoin.countDown();
                    assertThat(leaving.await(5, TimeUnit.SECONDS)).isTrue();
                    verify(twitch, never()).close();
                    verify(publishers, never()).close();
                    finishLeave.countDown();
                    return null;
                });
                robot.interact(() -> {
                    if (fail) assertThatThrownBy(helper::close).isSameAs(failure);
                    else assertThatCode(helper::close).doesNotThrowAnyException();
                });
                releasing.get(5, TimeUnit.SECONDS);
                robot.interact(() -> {
                    assertThat(model.isChatJoined()).isFalse();
                    assertThat(repository.getChannels()).isEmpty();
                });
                verify(chat).leaveChannel("channel");
                verify(twitch).close();
                verify(publishers).close();
                if (fail) assertThat(failure.getSuppressed()).containsExactly(releaseFailure);
            } finally {
                finishJoin.countDown(); finishLeave.countDown();
                robot.interact(repository::close);
                try { helper.close(); } catch (IllegalStateException expected) { assertThat(expected).isSameAs(failure); }
            }
        }
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release SDK operation");
    }
}
