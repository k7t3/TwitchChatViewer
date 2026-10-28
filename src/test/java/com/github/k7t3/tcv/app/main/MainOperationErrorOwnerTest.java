package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.TwitchChannelEvent;
import com.github.k7t3.tcv.support.ClipEventSource;
import com.github.twitch4j.chat.TwitchChat;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainOperationErrorOwnerTest {
    private static final Broadcaster OWNER = new Broadcaster("123", "channel", "Channel", "", "");

    @Test
    void aClipRetryFailureUsesTheMainWindowsErrorOwner(FxRobot robot) throws Exception {
        try (var fixture = new MainErrorFixture(robot)) {
            var client = mock(Twitch.class);
            var api = mock(TwitchAPI.class);
            when(client.getTwitchAPI()).thenReturn(api);
            when(client.getChat()).thenReturn(mock(TwitchChat.class));
            var failure = new IllegalStateException("clip lookup unavailable");
            when(api.getClips(List.of("clip"))).thenThrow(failure);
            var before = mock(VideoClip.class);
            var item = new AtomicReference<PostedClipItem>();
            var retry = new AtomicReference<FXTask<?>>();
            robot.interact(() -> {
                var repository = fixture.model.getClipRepository();
                repository.setSession(client);
                var source = new ClipEventSource(repository, client, OWNER);
                item.set(source.post("clip", before));

                retry.set(item.get().retry());
            });
            fixture.queue.remove().run();

            assertThatThrownBy(() -> retry.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            fixture.verifyErrorOwner();
            robot.interact(() -> assertThat(item.get().getClip()).isSameAs(before));
            verify(api).getClips(List.of("clip"));
        }
    }

    @Test
    void aChannelJoinFailureUsesTheMainWindowsErrorOwner(FxRobot robot) throws Exception {
        try (var fixture = new MainErrorFixture(robot)) {
            var domain = mock(ChannelRepository.class);
            var channel = mock(TwitchChannel.class);
            when(channel.getBroadcaster()).thenReturn(OWNER);
            when(domain.getOrLoadChannel(OWNER)).thenReturn(channel);
            var failure = new IllegalStateException("chat unavailable");
            when(channel.getOrJoinChatRoom()).thenThrow(failure);
            var loaded = new AtomicReference<FXTask<TwitchChannelViewModel>>();
            var joined = new AtomicReference<FXTask<?>>();
            robot.interact(() -> {
                fixture.model.getChannelRepository().setRepository(domain);
                loaded.set(fixture.model.getChannelRepository().getChannelAsync(OWNER));
            });
            fixture.queue.remove().run();
            var model = loaded.get().get(5, TimeUnit.SECONDS);

            robot.interact(() -> joined.set(model.joinChatAsync()));
            fixture.queue.remove().run();

            assertThatThrownBy(() -> joined.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            fixture.verifyErrorOwner();
        }
    }

    @Test
    void aLiveNotificationLookupFailureUsesTheMainWindowsErrorOwner(FxRobot robot) throws Exception {
        try (var fixture = new MainErrorFixture(robot)) {
            var domain = mock(ChannelRepository.class);
            var channel = mock(TwitchChannel.class);
            when(channel.getBroadcaster()).thenReturn(OWNER);
            when(channel.isActiveIn(domain)).thenReturn(true);
            var failure = new IllegalStateException("live channel lookup unavailable");
            when(domain.getOrLoadChannel(OWNER)).thenThrow(failure);
            var events = mock(EventSubscribers.class);
            var subscriptions = new ArrayList<Flow.Subscriber<TwitchChannelEvent>>();
            doAnswer(call -> {
                Flow.Subscriber<TwitchChannelEvent> subscriber = call.getArgument(0);
                subscriptions.add(subscriber);
                subscriber.onSubscribe(mock(Flow.Subscription.class));
                return null;
            }).when(events).subscribeChannelEvent(any());
            robot.interact(() -> {
                fixture.model.getChannelRepository().setRepository(domain);
                fixture.model.getLiveNotificator().subscribeEvents(events);

                var event = new ChannelOnlineEvent(channel, mock(StreamInfo.class));
                subscriptions.forEach(subscriber -> subscriber.onNext(event));
            });
            var work = fixture.queue.poll(5, TimeUnit.SECONDS);
            assertThat(work).as("live notification lookup work").isNotNull();
            work.run();

            fixture.verifyErrorOwner();
            verify(domain).getOrLoadChannel(OWNER);
            robot.interact(() -> assertThat(fixture.model.getLiveNotificator().getRecords()).isEmpty());
        }
    }
}
