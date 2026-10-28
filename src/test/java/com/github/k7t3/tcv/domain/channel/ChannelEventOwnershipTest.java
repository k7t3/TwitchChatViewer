package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventProvider;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChannelEventOwnershipTest {
    private static final Broadcaster BROADCASTER = new Broadcaster("123", "channel", "Channel", "", "");

    @Test
    void channelEventsBelongOnlyToTheirOriginalLiveRepository() {
        var owner = mock(ChannelRepository.class);
        var channel = new TwitchChannel(mock(Twitch.class), BROADCASTER, null, mock(ChatRoomEventProvider.class), owner);
        try {
            assertThat(channel.isActiveIn(owner)).isTrue();
            assertThat(channel.isActiveIn(mock(ChannelRepository.class))).isFalse();
            assertThat(channel.isActiveIn(null)).isFalse();
            channel.dispose();
            assertThat(channel.isActiveIn(owner)).isFalse();
        } finally { channel.dispose(); }
    }

    @Test
    void eventOwnershipDoesNotWaitForChatIo() throws Exception {
        var owner = mock(ChannelRepository.class);
        var twitch = mock(Twitch.class, RETURNS_DEEP_STUBS);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var chat = twitch.getChat();
        doAnswer(invocation -> { entered.countDown(); release.await(); return null; }).when(chat).joinChannel("channel");
        var channel = new TwitchChannel(twitch, BROADCASTER, null, mock(ChatRoomEventProvider.class), owner);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var joining = workers.submit(channel::getOrJoinChatRoom);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(workers.submit(() -> channel.isActiveIn(owner)).get(5, TimeUnit.SECONDS)).isTrue();
            } finally { release.countDown(); }
            joining.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); channel.dispose(); }
    }
}
