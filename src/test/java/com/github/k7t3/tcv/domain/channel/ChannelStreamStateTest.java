package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchClients;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventProvider;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.StreamStateUpdateEvent;
import com.github.k7t3.tcv.domain.event.stream.StreamStateEvent;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.philippheuer.events4j.core.EventManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ChannelStreamStateTest {
    private static final Broadcaster OWNER = new Broadcaster("123", "channel", "Channel", "", "");

    @Test
    void onlineUpdatesTheChannelSnapshotBeforePublishing() {
        try (var fixture = new Fixture(null)) {
            var stream = stream("Now live", "Game", 10);
            doAnswer(call -> {
                assertThat(fixture.channel.getStream()).isSameAs(stream);
                assertThat(fixture.channel.isStreaming()).isTrue();
                return null;
            }).when(fixture.publishers).submit(any(ChannelOnlineEvent.class));

            fixture.deliver(new StreamStateEvent.Online(OWNER.getUserId(), stream));

            assertThat(fixture.channel.getStream()).isSameAs(stream);
            verify(fixture.publishers).submit(argThat((ChannelOnlineEvent event) ->
                    event.getChannel() == fixture.channel && event.getInfo() == stream));
        }
    }

    @Test
    void offlineClearsTheChannelSnapshotBeforePublishing() {
        try (var fixture = new Fixture(stream("Previously live", "Game", 10))) {
            doAnswer(call -> {
                assertThat(fixture.channel.getStream()).isNull();
                assertThat(fixture.channel.isStreaming()).isFalse();
                return null;
            }).when(fixture.publishers).submit(any(ChannelOfflineEvent.class));

            fixture.deliver(new StreamStateEvent.Offline(OWNER.getUserId()));

            assertThat(fixture.channel.getStream()).isNull();
            verify(fixture.publishers).submit(argThat((ChannelOfflineEvent event) ->
                    event.getChannel() == fixture.channel));
        }
    }

    @ParameterizedTest(name = "{0} updates the snapshot before publication")
    @MethodSource("streamChanges")
    void streamChangesUpdateTheChannelSnapshotBeforePublishing(String change, StreamStateEvent event,
                                                              StreamInfo expected) {
        try (var fixture = new Fixture(stream("Old title", "Old game", 10))) {
            doAnswer(call -> {
                assertThat(fixture.channel.getStream()).isSameAs(expected);
                return null;
            }).when(fixture.publishers).submit(any(StreamStateUpdateEvent.class));

            fixture.deliver(event);

            assertThat(fixture.channel.getStream()).isSameAs(expected);
            assertThat(fixture.channel.isStreaming()).isTrue();
            verify(fixture.publishers).submit(argThat((StreamStateUpdateEvent published) ->
                    published.getChannel() == fixture.channel && published.getInfo() == expected));
        }
    }

    private static Stream<Arguments> streamChanges() {
        var title = stream("New title", "Old game", 10);
        var game = stream("Old title", "New game", 10);
        var viewers = stream("Old title", "Old game", 42);
        return Stream.of(
                Arguments.of("title", new StreamStateEvent.Title(OWNER.getUserId(), title), title),
                Arguments.of("game", new StreamStateEvent.Game(OWNER.getUserId(), game), game),
                Arguments.of("viewers", new StreamStateEvent.Viewers(OWNER.getUserId(), viewers), viewers));
    }

    @ParameterizedTest(name = "retired callbacks leave the snapshot unchanged, cleaned={0}")
    @ValueSource(booleans = {false, true})
    void retiredCallbacksCannotUpdateTheChannelSnapshot(boolean cleaned) {
        try (var fixture = new Fixture(null)) {
            if (cleaned) fixture.provider.clean();
            else fixture.provider.remove(fixture.channel);

            fixture.deliver(new StreamStateEvent.Online(OWNER.getUserId(), stream("Late title", "Game", 10)));

            assertThat(fixture.channel.getStream()).isNull();
            assertThat(fixture.channel.isStreaming()).isFalse();
            verifyNoInteractions(fixture.publishers);
        }
    }

    private static StreamInfo stream(String title, String game, int viewers) {
        return new StreamInfo(OWNER.getUserId(), "game-id", game, title, List.of("Japanese"), viewers,
                LocalDateTime.of(2026, 10, 31, 12, 0), Duration.ofMinutes(10), "ja");
    }

    private static final class Fixture implements AutoCloseable {
        private final EventPublishers publishers = mock(EventPublishers.class);
        private final Map<Class<?>, Consumer<Object>> callbacks = new HashMap<>();
        private final TwitchChannelEventProvider provider;
        private final TwitchChannel channel;

        private Fixture(StreamInfo initial) {
            var twitch = mock(Twitch.class);
            var clients = mock(TwitchClients.class);
            var manager = mock(EventManager.class);
            when(twitch.getClient()).thenReturn(clients);
            when(clients.getEventManager()).thenReturn(manager);
            doAnswer(call -> {
                callbacks.put(call.getArgument(0), call.getArgument(1));
                return mock(IEventSubscription.class);
            }).when(manager).onEvent(any(), any());
            channel = new TwitchChannel(twitch, OWNER, initial, mock(ChatRoomEventProvider.class),
                    mock(ChannelRepository.class));
            provider = new TwitchChannelEventProvider(twitch, publishers);
            provider.add(channel);
            provider.listen();
        }

        private void deliver(StreamStateEvent event) {
            callbacks.get(event.getClass()).accept(event);
        }

        @Override
        public void close() {
            try { provider.clean(); }
            finally { channel.dispose(); }
        }
    }
}
