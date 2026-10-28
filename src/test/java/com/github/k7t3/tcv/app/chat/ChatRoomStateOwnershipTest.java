package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomStateUpdatedEvent;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatRoomStateOwnershipTest {
    @ParameterizedTest
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void queuedStateCannotUpdateAfterLeaveOrReplaceTheCurrentRoomsState(boolean left, boolean active, FxRobot robot) {
        var channel = mock(TwitchChannelViewModel.class);
        var domain = mock(TwitchChannel.class);
        when(channel.getChannel()).thenReturn(domain);
        var old = mock(ChatRoom.class);
        var next = mock(ChatRoom.class);
        var current = new AtomicReference<>(old);
        when(domain.getJoinedChatRoom()).thenAnswer(invocation -> Optional.ofNullable(current.get()));
        var model = new SingleChatRoomViewModel(mock(ChatRoomContainerViewModel.class), null, null, null, null, channel, null);
        robot.interact(() -> {
            doReturn(FXTask.empty()).when(channel).leaveChatAsync();
            if (!active) model.getRoomStates().add(ChatRoomState.EMOTE_ONLY);
            model.onStateUpdated(new ChatRoomStateUpdatedEvent(old, ChatRoomState.EMOTE_ONLY, active));
            if (left) model.leaveChatAsync();
            else current.set(next);
        });
        robot.interact(() -> {
            assertThat(model.getRoomStates().contains(ChatRoomState.EMOTE_ONLY)).isEqualTo(!active);
            if (left) assertThatThrownBy(model::joinChatAsync).isInstanceOf(IllegalStateException.class);
            else model.onStateUpdated(new ChatRoomStateUpdatedEvent(next, ChatRoomState.EMOTE_ONLY, active));
        });
        if (!left) robot.interact(() ->
                assertThat(model.getRoomStates().contains(ChatRoomState.EMOTE_ONLY)).isEqualTo(active));
    }
}
