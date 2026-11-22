package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.event.LiveNotificationEvent;
import com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.channel.*;
import de.saxsys.mvvmfx.MvvmFX;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class LiveStateNotificatorOwnershipTest {
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void queuedOnlineAndOfflineResultsCannotRestoreHistoryAfterTheOwnerEnds(boolean online, boolean close, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f);
                f.post(online); f.read.run();
                if (close) f.model.close(); else f.model.onLogout();
            });
            robot.interact(() -> {
                var f = reference.get();
                assertThat(f.model.getRecords()).isEmpty(); assertThat(f.events).isEmpty();
                assertThat(f.read.isCancelled()).isFalse();
            });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @Test
    void historyConsumersCannotMutateTheNotificatorsOwnedRecords(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                assertThatThrownBy(() -> f.model.getRecords().add(new LiveStateNotificator.LiveStateRecord(f.channel, java.time.LocalTime.NOON, true)))
                        .isInstanceOf(UnsupportedOperationException.class);
                assertThat(f.model.getRecords()).isEmpty();
            }
        });
    }

    @Test
    void alternatingEventsKeepTheirOrderAndOnlyTheLatestFortyRecords(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f);
                for (int i = 0; i < 42; i++) f.post(i % 2 == 0);
                f.read.run();
            });
            robot.interact(() -> {
                var f = reference.get();
                assertThat(f.events).hasSize(42); assertThat(f.model.getRecords()).hasSize(40);
                for (int i = 0; i < 40; i++) {
                    var record = f.model.getRecords().get(i);
                    assertThat(record.channel()).isSameAs(f.channel);
                    assertThat(record.live()).isEqualTo(i % 2 == 0);
                    assertThat(record.time()).isNotNull();
                }
            });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void endingTheOwnerClearsHistoryThatWasAlreadyPublished(boolean close, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> { var f = new Fixture(); reference.set(f); f.post(true); f.read.run(); });
            robot.interact(() -> {
                var f = reference.get(); assertThat(f.model.getRecords()).hasSize(1);
                if (close) f.model.close(); else f.model.onLogout();
                assertThat(f.model.getRecords()).isEmpty(); assertThat(f.events).hasSize(1);
            });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @Test
    void foreignOrRetiredEventSourcesCannotStartAChannelLookup(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                when(f.source.isActiveIn(f.owner)).thenReturn(false);
                f.post(true); f.post(false);
                verify(f.repository, never()).getChannelAsync(any());
                assertThat(f.model.getRecords()).isEmpty(); assertThat(f.events).isEmpty();
            }
        });
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"source", "owner", "model"})
    void aLateLookupMustStillMatchItsEventSourceOwnerAndModel(String changed, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f); f.post(true);
                switch (changed) {
                    case "source" -> when(f.channel.getChannel()).thenReturn(mock(TwitchChannel.class));
                    case "owner" -> when(f.repository.getRepository()).thenReturn(mock(ChannelRepository.class));
                    case "model" -> when(f.channel.isRetiring()).thenReturn(true);
                    default -> throw new AssertionError(changed);
                }
                f.read.run();
            });
            robot.interact(() -> { assertThat(reference.get().model.getRecords()).isEmpty(); assertThat(reference.get().events).isEmpty(); });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @Test
    void closingFromAHistoryChangeCannotPublishItsPendingNotification(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f);
                f.model.getRecords().addListener((javafx.collections.ListChangeListener<LiveStateNotificator.LiveStateRecord>) change -> {
                    while (change.next()) if (change.wasAdded()) f.model.close();
                });
                f.post(true); f.read.run();
            });
            robot.interact(() -> { assertThat(reference.get().model.getRecords()).isEmpty(); assertThat(reference.get().events).isEmpty(); });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @Test
    void oneSharedLookupFailureIsReportedOnceAndCannotCreateHistory(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        var failure = new IllegalStateException("notification lookup unavailable");
        var task = FXTask.<TwitchChannelViewModel>task(() -> { throw failure; });
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(task);
                f.post(true); f.post(false); task.run();
            });
            robot.interact(() -> {
                var f = reference.get(); assertThat(f.errors).containsExactly(failure);
                assertThat(f.model.getRecords()).isEmpty(); assertThat(f.events).isEmpty();
            });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void anEndedOwnerCannotDisplayAnAlreadyCompletedFailure(boolean close, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        var task = FXTask.<TwitchChannelViewModel>task(() -> { throw new IllegalStateException("late lookup failure"); });
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(task);
                f.post(true); task.run();
                if (close) f.model.close(); else f.model.onLogout();
            });
            robot.interact(() -> assertThat(reference.get().errors).isEmpty());
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @Test
    void aNewSubscriptionCannotReceiveThePreviousScopesPendingNotice(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var f = new Fixture(); reference.set(f);
                f.post(true); f.model.onLogout(); f.start(); f.post(false); f.read.run();
            });
            robot.interact(() -> {
                var f = reference.get(); assertThat(f.model.getRecords()).hasSize(1);
                assertThat(f.model.getRecords().getFirst().live()).isFalse(); assertThat(f.events).hasSize(1);
                assertThat(f.read.isCancelled()).isFalse();
            });
        } finally { robot.interact(() -> { if (reference.get() != null) reference.get().close(); }); }
    }

    @Test
    void reopeningHistoryUsesTheCurrentModelInsteadOfItsRetiredModel(FxRobot robot) throws Exception {
        var f = published(robot);
        var fresh = mock(TwitchChannelViewModel.class); var freshSource = mock(TwitchChannel.class);
        when(fresh.getChannel()).thenReturn(freshSource); when(fresh.getBroadcaster()).thenReturn(f.broadcaster);
        when(freshSource.isActiveIn(f.owner)).thenReturn(true); when(fresh.isLive()).thenReturn(true);
        var fetch = FXTask.task(() -> fresh); var result = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> {
                when(f.channel.isRetiring()).thenReturn(true);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                result.set(f.model.openChannelAsync(f.model.getRecords().getFirst()));
                assertThat(f.model.openChannelAsync(f.model.getRecords().getFirst())).isSameAs(result.get());
            });
            fetch.run(); assertThat(result.get().get(5, java.util.concurrent.TimeUnit.SECONDS)).isSameAs(fresh);
            robot.interact(() -> {
                assertThat(f.opened).containsExactly(fresh);
                assertThat(f.opened).doesNotContain(f.channel);
            });
        } finally { robot.interact(f::close); }
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void endingTheScopeCancelsHistoryOpeningWithoutCancellingSharedLookup(boolean close, boolean resultReady, FxRobot robot) {
        var f = published(robot); var fetch = FXTask.task(() -> f.channel);
        try {
            robot.interact(() -> {
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                var result = f.model.openChannelAsync(f.model.getRecords().getFirst());
                if (resultReady) fetch.run();
                if (close) f.model.close(); else f.model.onLogout();
                assertThat(result.isCancelled()).isTrue(); assertThat(fetch.isCancelled()).isFalse();
                if (!resultReady) fetch.run();
            });
            robot.interact(() -> assertThat(f.opened).isEmpty());
        } finally { robot.interact(f::close); }
    }

    @Test
    void anAlreadyCancelledSharedLookupAlsoTerminatesTheHistoryOpening(FxRobot robot) {
        var f = published(robot);
        try {
            robot.interact(() -> {
                var fetch = FXTask.task(() -> f.channel); fetch.cancel(false);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                var result = f.model.openChannelAsync(f.model.getRecords().getFirst());
                assertThat(result.isCancelled()).isTrue(); assertThat(f.opened).isEmpty(); assertThat(f.errors).isEmpty();
            });
        } finally { robot.interact(f::close); }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"offline", "retired", "foreign", "differentId"})
    void historyCannotOpenAResultThatNoLongerRepresentsAnActiveLiveChannel(String change, FxRobot robot) throws Exception {
        var f = published(robot); var fetch = FXTask.task(() -> f.channel);
        var result = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> {
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                result.set(f.model.openChannelAsync(f.model.getRecords().getFirst()));
                switch (change) {
                    case "offline" -> when(f.channel.isLive()).thenReturn(false);
                    case "retired" -> when(f.channel.isRetiring()).thenReturn(true);
                    case "foreign" -> when(f.source.isActiveIn(f.owner)).thenReturn(false);
                    case "differentId" -> when(f.channel.getBroadcaster()).thenReturn(new Broadcaster("other", "other", "Other", "", ""));
                    default -> throw new AssertionError(change);
                }
            });
            fetch.run(); assertThat(result.get().get(5, java.util.concurrent.TimeUnit.SECONDS)).isNull();
            robot.interact(() -> { assertThat(f.opened).isEmpty(); assertThat(f.errors).isEmpty(); });
        } finally { robot.interact(f::close); }
    }

    @Test
    void cancellingOneHistoryOpeningAllowsRetryWithoutCancellingTheSharedLookup(FxRobot robot) throws Exception {
        var f = published(robot); var fetch = FXTask.task(() -> f.channel);
        var result = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> {
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                var record = f.model.getRecords().getFirst(); var first = f.model.openChannelAsync(record);
                first.cancel(true); result.set(f.model.openChannelAsync(record));
                assertThat(result.get()).isNotSameAs(first); assertThat(fetch.isCancelled()).isFalse();
            });
            fetch.run(); assertThat(result.get().get(5, java.util.concurrent.TimeUnit.SECONDS)).isSameAs(f.channel);
            robot.interact(() -> assertThat(f.opened).containsExactly(f.channel));
        } finally { robot.interact(f::close); }
    }

    @Test
    void aSharedFailureForAnOpeningAndANoticeReportsTheOriginalFailureOnlyOnce(FxRobot robot) throws Exception {
        var f = published(robot); var failure = new IllegalStateException("history lookup unavailable");
        var fetch = FXTask.<TwitchChannelViewModel>task(() -> { throw failure; });
        var result = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            robot.interact(() -> {
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                result.set(f.model.openChannelAsync(f.model.getRecords().getFirst())); f.post(false);
            });
            fetch.run();
            assertThatThrownBy(() -> result.get().get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseReference(failure);
            robot.interact(() -> {
                assertThat(f.errors).containsExactly(failure); assertThat(f.opened).isEmpty();
                assertThat(f.model.getRecords()).hasSize(1); assertThat(f.events).hasSize(1);
            });
        } finally { robot.interact(f::close); }
    }

    @Test
    void anEvictedOrForeignHistoryRecordCannotStartAChannelLookup(FxRobot robot) {
        var f = published(robot);
        try {
            robot.interact(() -> {
                var record = f.model.getRecords().getFirst();
                var foreign = new LiveStateNotificator.LiveStateRecord(record.channel(), record.time(), record.live());
                clearInvocations(f.repository);
                assertThat(f.model.canOpen(foreign)).isFalse();
                assertThat(f.model.openChannelAsync(foreign).isCancelled()).isTrue();
                f.model.onLogout();
                assertThat(f.model.openChannelAsync(record).isCancelled()).isTrue();
                verify(f.repository, never()).getChannelAsync(any());
            });
        } finally { robot.interact(f::close); }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void anAlreadyCompletedLookupDeliversItsOpeningOrOriginalFailure(boolean failed, FxRobot robot) throws Exception {
        var f = published(robot); var failure = new IllegalStateException("already failed history lookup");
        var fetch = FXTask.<TwitchChannelViewModel>task(() -> { if (failed) throw failure; return f.channel; });
        var result = new AtomicReference<FXTask<TwitchChannelViewModel>>();
        try {
            fetch.run();
            robot.interact(() -> {
                assertThat(fetch.getState()).isEqualTo(failed ? javafx.concurrent.Worker.State.FAILED : javafx.concurrent.Worker.State.SUCCEEDED);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                result.set(f.model.openChannelAsync(f.model.getRecords().getFirst()));
            });
            if (failed) assertThatThrownBy(() -> result.get().get(5, java.util.concurrent.TimeUnit.SECONDS)).hasCauseReference(failure);
            else assertThat(result.get().get(5, java.util.concurrent.TimeUnit.SECONDS)).isSameAs(f.channel);
            robot.interact(() -> {
                if (failed) { assertThat(f.errors).containsExactly(failure); assertThat(f.opened).isEmpty(); }
                else { assertThat(f.errors).isEmpty(); assertThat(f.opened).containsExactly(f.channel); }
            });
        } finally { robot.interact(f::close); }
    }

    @Test
    void aLookupCancellationEndsOnlyTheOpeningThatWasObservingIt(FxRobot robot) {
        var f = published(robot);
        try {
            robot.interact(() -> {
                var fetch = FXTask.task(() -> f.channel);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                var result = f.model.openChannelAsync(f.model.getRecords().getFirst());
                fetch.cancel(false);
                assertThat(result.isCancelled()).isTrue(); assertThat(f.opened).isEmpty(); assertThat(f.errors).isEmpty();
                assertThat(f.model.getRecords()).hasSize(1);
            });
        } finally { robot.interact(f::close); }
    }

    @Test
    void aLookupCancelledOffFxEndsItsOpeningBeforeFxTerminalDelivery(FxRobot robot) {
        var f = published(robot);
        try {
            robot.interact(() -> {
                var fetch = FXTask.task(() -> f.channel);
                when(f.repository.getChannelAsync(f.broadcaster)).thenReturn(fetch);
                var result = f.model.openChannelAsync(f.model.getRecords().getFirst());
                var finished = new java.util.concurrent.CompletableFuture<Void>();
                var worker = Thread.startVirtualThread(() -> {
                    try { fetch.cancel(false);finished.complete(null); }
                    catch (Throwable failure) { finished.completeExceptionally(failure); }
                });

                assertThatCode(() -> finished.get(5, java.util.concurrent.TimeUnit.SECONDS)).doesNotThrowAnyException();
                assertThatCode(() -> worker.join(java.time.Duration.ofSeconds(5))).doesNotThrowAnyException();

                assertThat(worker.isAlive()).isFalse();
                assertThat(result.isCancelled()).isTrue();
                assertThat(result.completion().toCompletableFuture()).isCompletedExceptionally();
                assertThat(f.opened).isEmpty();assertThat(f.errors).isEmpty();
                assertThat(f.model.getRecords()).hasSize(1);
            });
        } finally { robot.interact(f::close); }
    }

    private static Fixture published(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        robot.interact(() -> { var f = new Fixture(); reference.set(f); f.post(true); f.read.run(); });
        robot.interact(() -> assertThat(reference.get().model.getRecords()).hasSize(1));
        return reference.get();
    }

    private static final class Fixture implements AutoCloseable {
        final Broadcaster broadcaster = new Broadcaster("123", "channel", "Channel", "", "");
        final ChannelRepository owner = mock(ChannelRepository.class);
        final ChannelViewModelRepository repository = mock(ChannelViewModelRepository.class);
        final TwitchChannel source = mock(TwitchChannel.class);
        final TwitchChannelViewModel channel = mock(TwitchChannelViewModel.class);
        final FXTask<TwitchChannelViewModel> read = FXTask.task(() -> channel);
        final List<LiveNotificationEvent> events = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        final List<TwitchChannelViewModel> opened = new ArrayList<>();
        final TypeSafeNotificationObserver<com.github.k7t3.tcv.app.event.ChatOpeningEvent> openingObserver = event -> opened.addAll(event.getChannels());
        final TypeSafeNotificationObserver<LiveNotificationEvent> observer = events::add;
        final List<Flow.Subscriber<TwitchChannelEvent>> subscriptions = new ArrayList<>();
        final LiveStateNotificator model;
        Fixture() {
            when(source.getBroadcaster()).thenReturn(broadcaster);
            when(source.isActiveIn(owner)).thenReturn(true);
            when(channel.getChannel()).thenReturn(source);
            when(channel.getBroadcaster()).thenReturn(broadcaster);
            when(channel.isLive()).thenReturn(true);
            when(repository.getRepository()).thenReturn(owner);
            when(repository.getChannelAsync(broadcaster)).thenReturn(read);
            model = new LiveStateNotificator(repository, errors::add);
            start();
            MvvmFX.getNotificationCenter().subscribe(LiveNotificationEvent.class.getName(), observer);
            MvvmFX.getNotificationCenter().subscribe(com.github.k7t3.tcv.app.event.ChatOpeningEvent.class.getName(), openingObserver);
        }
        void start() {
            var input = mock(EventSubscribers.class);
            doAnswer(invocation -> {
                Flow.Subscriber<TwitchChannelEvent> subscriber = invocation.getArgument(0);
                subscriptions.add(subscriber); subscriber.onSubscribe(mock(Flow.Subscription.class)); return null;
            }).when(input).subscribeChannelEvent(any());
            model.subscribeEvents(input);
        }
        void post(boolean online) {
            TwitchChannelEvent event = online ? new ChannelOnlineEvent(source, mock(StreamInfo.class)) : new ChannelOfflineEvent(source);
            for (var subscriber : List.copyOf(subscriptions)) subscriber.onNext(event);
        }
        @Override public void close() {
            model.close();
            MvvmFX.getNotificationCenter().unsubscribe(LiveNotificationEvent.class.getName(), observer);
            MvvmFX.getNotificationCenter().unsubscribe(com.github.k7t3.tcv.app.event.ChatOpeningEvent.class.getName(), openingObserver);
        }
    }
}
