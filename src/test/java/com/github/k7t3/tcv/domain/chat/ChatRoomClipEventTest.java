package com.github.k7t3.tcv.domain.chat;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.chat.ClipPostedEvent;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRoomClipEventTest {
    @Test
    void receivingAClipPostsItsLinkWithoutRunningAnApiRequestOnTheSdkCallback() {
        var twitch = mock(Twitch.class, RETURNS_DEEP_STUBS);
        var publishers = mock(EventPublishers.class);
        var manager = twitch.getChat().getEventManager();
        var callback = new AtomicReference<Consumer<ChannelMessageEvent>>();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == ChannelMessageEvent.class) callback.set(invocation.getArgument(1));
            return mock(IEventSubscription.class);
        }).when(manager).onEvent(any(), any());
        var api = twitch.getTwitchAPI();
        when(api.getClips(any())).thenThrow(new AssertionError("SDK callback must not call the API"));
        var provider = new ChatRoomEventProvider(twitch, publishers);
        var owner = new Broadcaster("123", "owner", "Owner", "", "");
        var room = new ChatRoom(twitch, owner, mock(TwitchChannel.class));
        var event = mock(ChannelMessageEvent.class, RETURNS_DEEP_STUBS);
        var text = "watch https://clips.twitch.tv/ExampleClip please";
        when(event.getChannel().getId()).thenReturn("123");
        when(event.getMessage()).thenReturn(text);
        when(event.getMessageEvent().getMessage()).thenReturn(Optional.of(text));
        when(event.getMessageEvent().getFiredAtInstant()).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
        try {
            provider.listen(); provider.onJoined(room);
            callback.get().accept(event);
            var clip = ArgumentCaptor.forClass(ClipPostedEvent.class);
            verify(publishers).submit(clip.capture());
            assertThat(clip.getValue().getChatRoom()).isSameAs(room);
            var message = clip.getValue().getClipChatMessage();
            assertThat(message.getId()).isEqualTo("ExampleClip");
            assertThat(message.getEstimatedURL()).isEqualTo("https://clips.twitch.tv/ExampleClip");
            assertThat(message.getPlainMessage()).isEqualTo(text);
            assertThat(message.getClip()).isEmpty();
            verify(api, never()).getClips(any());
        } finally { provider.clean(); }
    }
}
