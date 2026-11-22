package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomStateUpdatedEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MergedRoomStateRoutingTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void routingRoomStateDoesNotJoinAChannelOnTheFxThreadOrUseAnOldRoom(boolean current, FxRobot robot) {
        robot.interact(() -> {
            var domain = mock(TwitchChannel.class);
            var channel = mock(TwitchChannelViewModel.class);
            when(channel.getChannel()).thenReturn(domain);
            when(domain.isChatJoined()).thenReturn(true);
            doThrow(new AssertionError("state routing must not join a room")).when(domain).getOrJoinChatRoom();
            var room = mock(ChatRoom.class);
            when(domain.getJoinedChatRoom()).thenReturn(Optional.of(current ? room : mock(ChatRoom.class)));
            var child = mock(SingleChatRoomViewModel.class);
            var model = new MergedChatRoomViewModel(null, null, null, List.of(), null,
                    mock(ChatRoomContainerViewModel.class), null);
            model.getChannels().put(channel, child);
            var event = new ChatRoomStateUpdatedEvent(room, ChatRoomState.EMOTE_ONLY, true);

            model.onStateUpdated(event);

            verify(domain, never()).getOrJoinChatRoom();
            if (current) verify(child).onStateUpdated(event);
            else verify(child, never()).onStateUpdated(event);
        });
    }
}
