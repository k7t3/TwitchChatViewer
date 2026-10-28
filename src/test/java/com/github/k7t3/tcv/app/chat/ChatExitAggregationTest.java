package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.view.MainModelFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.app.event.KeywordFilteringEvent;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatExitAggregationTest {
    @Test
    void resourceReleaseFailureStillLeavesRoomsAndUnsubscribesSessionNotifications(FxRobot robot) {
        var emoji = mock(Emoji.class);
        var failure = new IllegalStateException("emoji archive could not close");
        doThrow(failure).when(emoji).close();
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var accepting = new AtomicBoolean(true);
        var releases = new ArrayList<CompletionStage<Void>>();
        var model = new ChatRoomContainerViewModel(() -> new ChatRoomContainerViewModel.LoadRequest(
                mock(ChatFilters.class), () -> resources), task -> {
            if (!accepting.get()) throw new RejectedExecutionException("closed");
            task.run();
        }, releases::add);
        var room = room();
        var afterLogout = room();
        robot.interact(() -> {
            doReturn(FXTask.empty()).when(room).leaveChatAsync();
            doReturn(FXTask.empty()).when(afterLogout).leaveChatAsync();
            model.subscribeEvents(mock(EventSubscribers.class));
            model.loadAsync();
        });
        try {
            robot.interact(() -> {
                assertThat(model.isLoaded()).isTrue();
                model.getChatRoomList().add(room);
                accepting.set(false);
                assertThatCode(model::onLogout).doesNotThrowAnyException();
                verify(room).leaveChatAsync();
                assertThat(model.getChatRoomList()).isEmpty();
                model.getFloatingChatRoomList().add(afterLogout);
                var event = new KeywordFilteringEvent(mock(KeywordFilterEntry.class));
                model.publish(event);
                verify(afterLogout, never()).chatFilter(event);
            });
            assertThatThrownBy(() -> CompletableFuture.allOf(
                    releases.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new))
                    .get(5, TimeUnit.SECONDS)).hasCause(failure);
        } finally { robot.interact(model::close); }
    }

    @Test
    void forcedStopDrainsRealWorkAndReportsFailureEvenAfterTheMergedObserverWasCancelled(FxRobot robot) throws Exception {
        var worker = spy(new TaskWorker());
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var model = MainModelFactory.createChat(helper, mock(ImageLoader.class));
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        var failure = new IllegalStateException("late merged leave failure");
        var child = FXTask.task(() -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release cleanup");
            throw failure;
        });
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        var channel = mock(TwitchChannelViewModel.class);
        doReturn(child).when(channel).leaveChatAsync();
        try (var coordinator = Executors.newSingleThreadExecutor()) {
            try {
                robot.interact(() -> {
                    var merged = new MergedChatRoomViewModel(null, null, null, List.of(), null, model, null);
                    merged.getChannels().put(channel, mock(SingleChatRoomViewModel.class));
                    model.getFloatingChatRoomList().add(merged);
                    helper.getExecutor().execute(child);
                    merged.leaveChatAsync().cancel();
                    model.close();
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var unblocking = coordinator.submit(() -> {
                    assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                    release.countDown();
                    return null;
                });
                robot.interact(() -> assertThatThrownBy(helper::close).isSameAs(failure));
                unblocking.get(5, TimeUnit.SECONDS);
                assertThat(child.isCancelled()).isFalse();
                verify(publishers).close();
            } finally {
                release.countDown();
                robot.interact(model::close);
                try { helper.close(); } catch (IllegalStateException expected) { assertThat(expected).isSameAs(failure); }
            }
        }
    }

    @Test
    void individualSingleExitIsDetachedAndItsResultIsOwnedByTheContainer(FxRobot robot) {
        var helper = mock(AppHelper.class);
        when(helper.getExecutor()).thenReturn(Runnable::run);
        var model = MainModelFactory.createChat(helper, mock(ImageLoader.class));
        var channel = mock(TwitchChannelViewModel.class);
        var failure = new IllegalStateException("individual exit failed");
        var child = FXTask.task(() -> { throw failure; });
        doReturn(child).when(channel).leaveChatAsync();
        var releases = new ArrayList<CompletionStage<Void>>();
        doAnswer(invocation -> { releases.add(invocation.getArgument(0)); return null; })
                .when(helper).awaitOnShutdown(any());
        try {
            robot.interact(() -> {
                var single = new SingleChatRoomViewModel(model, null, null, null, null, channel, null);
                model.getChatRoomList().add(single);
                single.leaveChatAsync().cancel();
                assertThat(model.getChatRoomList()).isEmpty();
                verify(helper).awaitOnShutdown(any());
            });
            assertThat(child.isCancelled()).isFalse();
            child.run();
            assertThatThrownBy(() -> releases.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
        } finally { child.run(); robot.interact(model::close); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"selected", "logout", "close"})
    void everyExitPathStartsAllRoomsAndRetainsFailuresForShutdown(String path, FxRobot robot) {
        var helper = mock(AppHelper.class);
        when(helper.getExecutor()).thenReturn(Runnable::run);
        var releases = new ArrayList<CompletionStage<Void>>();
        doAnswer(invocation -> { releases.add(invocation.getArgument(0)); return null; })
                .when(helper).awaitOnShutdown(any());
        var model = MainModelFactory.createChat(helper, mock(ImageLoader.class));
        var first = room();
        var last = room();
        var firstFailure = new IllegalStateException("first exit did not start");
        var lastFailure = new IllegalArgumentException("last exit failed");
        var late = FXTask.task(() -> { throw lastFailure; });
        doThrow(firstFailure).when(first).leaveChatAsync();
        doReturn(late).when(last).leaveChatAsync();
        try {
            robot.interact(() -> {
                model.getChatRoomList().add(first);
                if (path.equals("selected")) model.getChatRoomList().add(last);
                else model.getFloatingChatRoomList().add(last);
                assertThatCode(() -> {
                    switch (path) {
                        case "selected" -> model.closeAll();
                        case "logout" -> model.onLogout();
                        case "close" -> model.close();
                        default -> throw new AssertionError(path);
                    }
                }).doesNotThrowAnyException();
                verify(first).leaveChatAsync();
                verify(last).leaveChatAsync();
                assertThat(model.getChatRoomList()).isEmpty();
                assertThat(model.getFloatingChatRoomList()).isEmpty();
            });
            assertThat(releases).hasSize(1);
            assertThat(releases.getFirst().toCompletableFuture().isDone()).isFalse();
            // This is also a simulated forced stop: no FX event can run while the owner waits here.
            robot.interact(() -> {
                Thread.ofVirtual().start(late);
                assertThatThrownBy(() -> releases.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS))
                        .hasCause(firstFailure);
            });
            assertThat(firstFailure.getSuppressed()).containsExactly(lastFailure);
        } finally {
            late.run();
            robot.interact(model::close);
        }
    }

    @Test
    void removingTheLastRoomDetachesItEvenIfItsExitCannotStart(FxRobot robot) {
        var helper = mock(AppHelper.class);
        when(helper.getExecutor()).thenReturn(Runnable::run);
        var model = MainModelFactory.createChat(helper, mock(ImageLoader.class));
        var room = room();
        doThrow(new IllegalStateException("leave failed")).when(room).leaveChatAsync();
        try {
            robot.interact(() -> {
                model.getChatRoomList().add(room);
                assertThatCode(model::removeLast).doesNotThrowAnyException();
                assertThat(model.getChatRoomList()).isEmpty();
                verify(helper).awaitOnShutdown(any());
            });
        } finally { robot.interact(model::close); }
    }

    private ChatRoomViewModel room() {
        var room = mock(ChatRoomViewModel.class);
        var selected = new SimpleBooleanProperty(true);
        when(room.selectedProperty()).thenReturn(selected);
        when(room.isSelected()).thenAnswer(ignored -> selected.get());
        return room;
    }
}
