package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.chat.ChatClearedEvent;
import com.github.k7t3.tcv.domain.event.chat.ChatMessageDeletedEvent;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.chat.events.channel.ClearChatEvent;
import com.github.twitch4j.chat.events.channel.DeleteMessageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatRoomModerationEventTest {
    @Test
    void aDeletionRetainsItsRoomAndMessageIdAcrossTheSdkBoundary() {
        try (var listening = listen(DeleteMessageEvent.class)) {
            var event = mock(DeleteMessageEvent.class, RETURNS_DEEP_STUBS);
            when(event.getChannel().getId()).thenReturn("123");
            when(event.getMsgId()).thenReturn("message-42");

            listening.callback().accept(event);

            var delivered = ArgumentCaptor.forClass(ChatMessageDeletedEvent.class);
            verify(listening.publishers()).submit(delivered.capture());
            assertThat(delivered.getValue().getChatRoom()).isSameAs(listening.room());
            assertThat(delivered.getValue().getDeletedMessageId()).isEqualTo("message-42");
        }
    }

    @Test
    void aClearNotificationRetainsItsRoomAcrossTheSdkBoundary() {
        try (var listening = listen(ClearChatEvent.class)) {
            var event = mock(ClearChatEvent.class, RETURNS_DEEP_STUBS);
            when(event.getChannel().getId()).thenReturn("123");

            listening.callback().accept(event);

            var delivered = ArgumentCaptor.forClass(ChatClearedEvent.class);
            verify(listening.publishers()).submit(delivered.capture());
            assertThat(delivered.getValue().getChatRoom()).isSameAs(listening.room());
        }
    }

    private static <E> Listening<E> listen(Class<E> type) {
        var twitch = mock(Twitch.class, RETURNS_DEEP_STUBS);
        var publishers = mock(EventPublishers.class);
        var callback = new AtomicReference<Consumer<E>>();
        var manager = twitch.getChat().getEventManager();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == type) callback.set(invocation.getArgument(1));
            return mock(IEventSubscription.class);
        }).when(manager).onEvent(any(), any());
        var provider = new ChatRoomEventProvider(twitch, publishers);
        var room = new ChatRoom(twitch, new Broadcaster("123", "owner", "Owner", "", ""), mock(TwitchChannel.class));
        provider.listen();
        provider.onJoined(room);
        return new Listening<>(provider, room, publishers, callback.get());
    }

    private record Listening<E>(ChatRoomEventProvider provider, ChatRoom room, EventPublishers publishers,
                                 Consumer<E> callback) implements AutoCloseable {
        @Override public void close() { provider.clean(); }
    }
}
