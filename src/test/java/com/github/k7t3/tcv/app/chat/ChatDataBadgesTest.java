package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatDataBadgesTest {
    @Test
    void channelBadgesTakePriorityAndMissingBadgesDoNotHideTheOthers(FxRobot robot) {
        robot.interact(() -> {
            var channelBadges = mock(ChannelChatBadgeStore.class);
            var globalBadges = mock(GlobalChatBadgeStore.class);
            var subscriber = new ChatBadge("subscriber", "12");
            var moderator = new ChatBadge("moderator", "1");
            var missing = new ChatBadge("missing", "1");
            var channelImage = mock(LazyImage.class);
            var globalImage = mock(LazyImage.class);
            when(channelBadges.getNullable(any())).thenReturn(Optional.empty());
            when(globalBadges.getNullable(any())).thenReturn(Optional.empty());
            when(channelBadges.getNullable(subscriber)).thenReturn(Optional.of(channelImage));
            when(globalBadges.getNullable(moderator)).thenReturn(Optional.of(globalImage));
            var data = new ChatData("channel", "channel", "message", "viewer", "Viewer", "viewer", "#112233",
                    List.of(subscriber, moderator, missing),
                    new ChatMessage("hello", List.of(ChatMessageFragment.text("hello"))), Instant.parse("2024-01-01T00:00:00Z"));

            var model = new ChatDataViewModel(mock(TwitchChannelViewModel.class), data, globalBadges, channelBadges,
                    null, null, null, null);

            assertThat(model.getBadges()).containsExactly(channelImage, globalImage);
            assertThat(model.getMessage().getPlain()).isEqualTo("hello");
            verify(globalBadges, never()).getNullable(subscriber);
        });
    }
}
