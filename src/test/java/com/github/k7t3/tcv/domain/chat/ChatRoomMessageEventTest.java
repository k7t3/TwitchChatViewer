package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatRoomMessageEventTest {
    @ParameterizedTest(name = "malformed metadata delivery [{index}]: {0}")
    @ValueSource(strings = {"broken/25:6-10", "invalid:2-1/25:6-10", "25:6-10,6-10"})
    void malformedEmoteMetadataDoesNotDiscardTheSdkMessage(String emotes) {
        var twitch = mock(Twitch.class, RETURNS_DEEP_STUBS);
        var publishers = mock(EventPublishers.class);
        var manager = twitch.getChat().getEventManager();
        var callback = new AtomicReference<Consumer<ChannelMessageEvent>>();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == ChannelMessageEvent.class) callback.set(invocation.getArgument(1));
            return mock(IEventSubscription.class);
        }).when(manager).onEvent(any(), any());
        var provider = new ChatRoomEventProvider(twitch, publishers);
        var room = new ChatRoom(twitch, new Broadcaster("123", "owner", "Owner", "", ""), mock(TwitchChannel.class));
        var event = mock(ChannelMessageEvent.class, RETURNS_DEEP_STUBS);
        var raw = event.getMessageEvent();
        var plain = "hello Kappa tail";
        var timestamp = Instant.parse("2024-01-01T00:00:00Z");
        when(event.getChannel().getId()).thenReturn("123");
        when(event.getMessage()).thenReturn(plain);
        when(raw.getMessage()).thenReturn(Optional.of(plain));
        when(raw.getTagValue("emotes")).thenReturn(Optional.of(emotes));
        when(raw.getChannelId()).thenReturn("123");
        when(raw.getChannelName()).thenReturn(Optional.of("owner"));
        when(raw.getMessageId()).thenReturn(Optional.of("message-1"));
        when(raw.getUserId()).thenReturn("viewer-1");
        when(raw.getUserName()).thenReturn("viewer");
        when(raw.getUserDisplayName()).thenReturn(Optional.of("Viewer"));
        when(raw.getUserChatColor()).thenReturn(Optional.of("#abcdef"));
        when(raw.getBadges()).thenReturn(Map.of("subscriber", "12"));
        when(raw.getFiredAtInstant()).thenReturn(timestamp);
        try {
            provider.listen();
            provider.onJoined(room);

            callback.get().accept(event);

            var delivered = ArgumentCaptor.forClass(ChatMessageEvent.class);
            verify(publishers).submitChatMessage(delivered.capture());
            assertThat(delivered.getValue().getChatRoom()).isSameAs(room);
            var data = delivered.getValue().getChatData();
            assertThat(data.channelId()).isEqualTo("123");
            assertThat(data.msgId()).isEqualTo("message-1");
            assertThat(data.userId()).isEqualTo("viewer-1");
            assertThat(data.firedAt()).isEqualTo(timestamp);
            assertThat(data.badges()).containsExactly(new ChatBadge("subscriber", "12"));
            assertThat(data.message().getPlain()).isEqualTo(plain);
            assertThat(data.message()).containsExactly(
                    ChatMessageFragment.text("hello "), ChatMessageFragment.emote("25", "Kappa"),
                    ChatMessageFragment.text(" tail"));
        } finally { provider.clean(); }
    }
}
