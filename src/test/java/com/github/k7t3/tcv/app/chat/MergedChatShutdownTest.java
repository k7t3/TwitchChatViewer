package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.service.TaskWorker;
import org.junit.jupiter.api.AutoClose;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(ApplicationExtension.class)
class MergedChatShutdownTest {
    @AutoClose
    private final TaskWorker worker = new TaskWorker();

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void closingOneRoomWithinAMergeStartsItsLeaveAndKeepsTheOtherRooms(int count, FxRobot robot) {
        robot.interact(() -> {
            var container = mock(ChatRoomContainerViewModel.class);
            var visible = FXCollections.<ChatRoomViewModel>observableArrayList();
            when(container.getChatRoomList()).thenReturn(visible);
            var model = new MergedChatRoomViewModel(null, null, null, List.of(), null, container, null);
            visible.add(model);
            var channel = mock(TwitchChannelViewModel.class);
            when(channel.leaveChatAsync()).thenReturn(FXTask.empty());
            var room = new SingleChatRoomViewModel(container, null, null, null, null, channel, null);
            model.getChannels().put(channel, room);
            var remaining = new java.util.ArrayList<SingleChatRoomViewModel>();
            for (int i = 1; i < count; i++) {
                var other = mock(TwitchChannelViewModel.class);
                var single = new SingleChatRoomViewModel(container, null, null, null, null, other, null);
                remaining.add(single);
                model.getChannels().put(other, single);
            }
            model.closeChatRoom(room);
            verify(channel).leaveChatAsync();
            assertThat(model.getChannels()).doesNotContainKey(channel);
            remaining.forEach(single -> verify(single.getChannel(), org.mockito.Mockito.never()).leaveChatAsync());
            if (count == 2) assertThat(visible).containsExactly(remaining.getFirst());
            else {
                assertThat(visible).containsExactly(model);
                assertThat(model.getChannels()).hasSize(2);
            }
            assertThatThrownBy(room::joinChatAsync).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void oneSynchronousLeaveFailureDoesNotPreventTheRemainingChannelsFromStarting(FxRobot robot) {
        var aggregate = new AtomicReference<FXTask<?>>();
        var failure = new IllegalStateException("first leave could not start");
        robot.interact(() -> {
            var model = new MergedChatRoomViewModel(null, null, null, List.of(), null,
                    mock(ChatRoomContainerViewModel.class), null);
            var first = mock(TwitchChannelViewModel.class); var next = mock(TwitchChannelViewModel.class);
            model.getChannels().put(first, mock(SingleChatRoomViewModel.class));
            model.getChannels().put(next, mock(SingleChatRoomViewModel.class));
            var order = List.copyOf(model.getChannels().keySet());
            doThrow(failure).when(order.getFirst()).leaveChatAsync();
            when(order.getLast().leaveChatAsync()).thenReturn(FXTask.empty());
            aggregate.set(model.leaveChatAsync());
            verify(order.getLast()).leaveChatAsync();
        });
        assertThatThrownBy(() -> aggregate.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
    }

    @Test
    void leaveReturnsImmediatelyAndCompletesAfterAllChannelsLeave(FxRobot robot) throws Exception {
        var firstRelease = new CountDownLatch(1);
        var secondRelease = new CountDownLatch(1);
        FXTask<Void> first = FXTask.task(() -> { firstRelease.await(); return null; });
        FXTask<Void> second = FXTask.task(() -> { secondRelease.await(); return null; });
        var firstChannel = mock(TwitchChannelViewModel.class);
        var secondChannel = mock(TwitchChannelViewModel.class);
        when(firstChannel.leaveChatAsync()).thenReturn(first);
        when(secondChannel.leaveChatAsync()).thenReturn(second);
        var model = new MergedChatRoomViewModel(null, null, null, List.of(), null,
                mock(ChatRoomContainerViewModel.class), null);
        var aggregate = new AtomicReference<FXTask<?>>();
        var fxProgress = new CountDownLatch(1);
        try {
            robot.interact(() -> {
                model.getChannels().put(firstChannel, mock(SingleChatRoomViewModel.class));
                model.getChannels().put(secondChannel, mock(SingleChatRoomViewModel.class));
                worker.getExecutor().execute(first);
                worker.getExecutor().execute(second);
            });

            Platform.runLater(() -> aggregate.set(model.leaveChatAsync()));
            Platform.runLater(fxProgress::countDown);

            assertThat(fxProgress.await(1, TimeUnit.SECONDS)).as("FX event following merged leave").isTrue();
            assertThat(aggregate.get().isDone()).isFalse();
            firstRelease.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(aggregate.get().isDone()).isFalse();
            secondRelease.countDown();
            aggregate.get().get(5, TimeUnit.SECONDS);
        } finally {
            firstRelease.countDown();
            secondRelease.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            robot.interact(() -> {});
        }
    }
}
