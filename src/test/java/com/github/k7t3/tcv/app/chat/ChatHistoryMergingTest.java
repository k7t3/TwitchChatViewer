package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatHistoryMergingTest {
    @Test
    void aggregatingMergedRoomsKeepsTheirLiveHistoryWithoutRewritingChildHistory(FxRobot robot) {
        robot.interact(() -> {
            var container = mock(ChatRoomContainerViewModel.class);
            var visible = FXCollections.<ChatRoomViewModel>observableArrayList();
            when(container.getChatRoomList()).thenReturn(visible);
            var firstChannel = mock(TwitchChannelViewModel.class);
            var nextChannel = mock(TwitchChannelViewModel.class);
            var firstRoom = new SingleChatRoomViewModel(container, null, null, null, null, firstChannel, null);
            var nextRoom = new SingleChatRoomViewModel(container, null, null, null, null, nextChannel, null);
            var firstOld = message(firstChannel, "first-old", 1);
            var nextOld = message(nextChannel, "next-old", 2);
            firstRoom.addChat(firstOld);
            nextRoom.addChat(nextOld);
            var target = new MergedChatRoomViewModel(null, null, null, List.of(firstRoom), null, container, null);
            var source = new MergedChatRoomViewModel(null, null, null, List.of(nextRoom), null, container, null);
            target.setChatCacheSize(3);
            var firstLive = message(firstChannel, "first-live", 4);
            var nextLive = message(nextChannel, "next-live", 3);
            target.addChat(firstLive);
            source.addChat(nextLive);
            visible.addAll(target, source);

            target.aggregate(source);

            assertThat(target.getChatDataList()).containsExactly(nextOld, nextLive, firstLive);
            assertThat(firstRoom.getChatDataList()).containsExactly(firstOld);
            assertThat(nextRoom.getChatDataList()).containsExactly(nextOld);
            assertThat(target.getChannels()).containsOnlyKeys(firstChannel, nextChannel)
                    .containsEntry(firstChannel, firstRoom).containsEntry(nextChannel, nextRoom);
            assertThat(visible).containsExactly(target);
            verify(firstChannel, never()).leaveChatAsync();
            verify(nextChannel, never()).leaveChatAsync();
        });
    }

    @ParameterizedTest(name = "arrival retention in merged={0}")
    @ValueSource(booleans = {false, true})
    void receivingMessagesKeepsTheLatestItemsWithinTheChosenLimit(boolean merged, FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(TwitchChannelViewModel.class);
            var model = room(merged, channel);
            model.setChatCacheSize(3);
            var first = message(channel, "first", 1);
            var second = message(channel, "second", 2);
            var third = message(channel, "third", 3);
            var fourth = message(channel, "fourth", 4);
            var fifth = message(channel, "fifth", 5);

            List.of(first, second, third, fourth, fifth).forEach(model::addChat);

            assertThat(model.getChatDataList()).containsExactly(third, fourth, fifth);
        });
    }

    @ParameterizedTest(name = "shrinking and growing cache in merged={0}")
    @ValueSource(booleans = {false, true})
    void growingTheCacheDoesNotRestoreDiscardedMessages(boolean merged, FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(TwitchChannelViewModel.class);
            var model = room(merged, channel);
            model.setChatCacheSize(3);
            var first = message(channel, "first", 1);
            var second = message(channel, "second", 2);
            var third = message(channel, "third", 3);
            var fourth = message(channel, "fourth", 4);
            var fifth = message(channel, "fifth", 5);
            List.of(first, second, third).forEach(model::addChat);

            model.setChatCacheSize(1);
            assertThat(model.getChatDataList()).containsExactly(third);
            model.setChatCacheSize(3);
            assertThat(model.getChatDataList()).containsExactly(third);
            model.addChat(fourth);
            model.addChat(fifth);

            assertThat(model.getChatDataList()).containsExactly(third, fourth, fifth);
        });
    }

    @ParameterizedTest(name = "delete all displayed copies in merged={0}")
    @ValueSource(booleans = {false, true})
    void deletingAMessageMarksEveryDisplayedCopyWithoutDeletingOtherMessages(boolean merged, FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(TwitchChannelViewModel.class);
            var model = room(merged, channel);
            var firstCopy = message(channel, "same-message", 1);
            var other = message(channel, "other-message", 2);
            var secondCopy = message(channel, "same-message", 3);
            List.of(firstCopy, other, secondCopy).forEach(model::addChat);

            model.deleteChatMessage("same-message");

            assertThat(firstCopy.isDeleted()).isTrue();
            assertThat(secondCopy.isDeleted()).isTrue();
            assertThat(other.isDeleted()).isFalse();
            assertThat(model.getChatDataList()).containsExactly(firstCopy, other, secondCopy);
        });
    }

    @ParameterizedTest(name = "history presentation transfer: {0}")
    @ValueSource(strings = {"single", "merged", "separate"})
    void transferredHistoryFollowsTheDestinationPresentation(String transfer, FxRobot robot) {
        robot.interact(() -> {
            var container = mock(ChatRoomContainerViewModel.class);
            var visible = FXCollections.<ChatRoomViewModel>observableArrayList();
            when(container.getChatRoomList()).thenReturn(visible);
            var channel = mock(TwitchChannelViewModel.class);
            var first = new SingleChatRoomViewModel(container, null, null, null, null, channel, null);
            var other = new SingleChatRoomViewModel(container, null, null, null, null,
                    mock(TwitchChannelViewModel.class), null);
            ChatRoomViewModel source = transfer.equals("single") ? first
                    : new MergedChatRoomViewModel(null, null, null, List.of(first, other), null, container, null);
            ChatRoomViewModel destination = transfer.equals("separate") ? first
                    : new MergedChatRoomViewModel(null, null, null, List.of(), null, container, null);
            var item = message(channel, "retained", 1);
            source.setShowName(true);
            source.setShowBadges(true);
            source.addChat(item);
            destination.setShowName(false);
            destination.setShowBadges(false);
            visible.add(source);
            if (!transfer.equals("separate")) visible.add(destination);

            switch (transfer) {
                case "single" -> ((MergedChatRoomViewModel) destination).addChatRoom(first);
                case "merged" -> ((MergedChatRoomViewModel) destination).aggregate((MergedChatRoomViewModel) source);
                case "separate" -> ((MergedChatRoomViewModel) source).separateChatRoom(first);
                default -> throw new AssertionError(transfer);
            }

            assertThat(destination.getChatDataList()).containsExactly(item);
            assertThat(item.isVisibleName()).isFalse();
            assertThat(item.isVisibleBadge()).isFalse();
            destination.setShowName(true);
            destination.setShowBadges(true);
            assertThat(item.isVisibleName()).isTrue();
            assertThat(item.isVisibleBadge()).isTrue();
        });
    }

    @Test
    void clearingOneChannelKeepsOtherChannelsMessagesInTheMerge(FxRobot robot) {
        robot.interact(() -> {
            var container = mock(ChatRoomContainerViewModel.class);
            var firstChannel = mock(TwitchChannelViewModel.class);
            var nextChannel = mock(TwitchChannelViewModel.class);
            var firstBroadcaster = new Broadcaster("first", "first", "First", "", "");
            when(firstChannel.getBroadcaster()).thenReturn(firstBroadcaster);
            when(nextChannel.getBroadcaster()).thenReturn(new Broadcaster("next", "next", "Next", "", ""));
            var firstRoom = new SingleChatRoomViewModel(container, null, null, null, null, firstChannel, null);
            var nextRoom = new SingleChatRoomViewModel(container, null, null, null, null, nextChannel, null);
            var model = new MergedChatRoomViewModel(null, null, null, List.of(firstRoom, nextRoom), null, container, null);
            var first = message(firstChannel, "first", 1);
            var next = message(nextChannel, "next", 2);
            var last = message(firstChannel, "last", 3);
            List.of(first, next, last).forEach(model::addChat);
            var cleared = mock(ChatRoom.class);
            when(cleared.getBroadcaster()).thenReturn(firstBroadcaster);

            model.clearChatMessages(cleared);

            assertThat(model.getChatDataList()).containsExactly(next);
        });
    }

    @ParameterizedTest(name = "initial merge configured cache={0}")
    @ValueSource(ints = {1, 256, 513})
    void theInitialMergeUsesTheConfiguredLimitBeforeCollectingHistory(int limit, FxRobot robot) {
        robot.interact(() -> {
            var preferences = new AppPreferences(new MemoryPreferences()).getChatPreferences();
            preferences.setChatCacheSize(limit);
            var container = new ChatRoomContainerViewModel(() -> {
                throw new AssertionError("history merging must not start resource IO");
            }, Runnable::run, ignored -> {});
            container.bindChatPreferences(preferences);
            var firstChannel = mock(TwitchChannelViewModel.class);
            var nextChannel = mock(TwitchChannelViewModel.class);
            when(firstChannel.leaveChatAsync()).thenReturn(FXTask.empty());
            when(nextChannel.leaveChatAsync()).thenReturn(FXTask.empty());
            var first = new SingleChatRoomViewModel(container, null, null, null, null, firstChannel, null);
            var next = new SingleChatRoomViewModel(container, null, null, null, null, nextChannel, null);
            first.setChatCacheSize(limit);
            next.setChatCacheSize(limit);
            var history = new ArrayList<ChatDataViewModel>();
            for (var index = 1; index <= limit; index++) {
                var room = index <= limit / 2 ? first : next;
                var item = message(room.getChannel(), "message-" + index, index);
                history.add(item);
                room.addChat(item);
            }
            container.getChatRoomList().addAll(first, next);
            first.setSelected(true);
            next.setSelected(true);
            try {
                container.mergeSelectedChats();

                assertThat(container.getChatRoomList()).singleElement().isInstanceOfSatisfying(
                        MergedChatRoomViewModel.class, merged -> {
                            assertThat(merged.getChatCacheSize()).isEqualTo(limit);
                            assertThat(merged.getChatDataList()).containsExactlyElementsOf(history);
                        });
            } finally { container.close(); }
        });
    }

    private static ChatRoomViewModel room(boolean merged, TwitchChannelViewModel channel) {
        var container = mock(ChatRoomContainerViewModel.class);
        var single = new SingleChatRoomViewModel(container, null, null, null, null, channel, null);
        return merged ? new MergedChatRoomViewModel(null, null, null, List.of(single), null, container, null) : single;
    }

    private static ChatDataViewModel message(TwitchChannelViewModel channel, String id, int second) {
        var data = new ChatData("channel", "channel", id, "viewer", "Viewer", "viewer", "#000000",
                List.of(), new ChatMessage(id, List.of(ChatMessageFragment.text(id))),
                Instant.parse("2024-01-01T00:00:00Z").plusSeconds(second));
        return new ChatDataViewModel(channel, data, null, null, null, null, null, null);
    }
}
