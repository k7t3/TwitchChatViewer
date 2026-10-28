package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.event.stream.StreamStateEvent;
import com.github.twitch4j.helix.domain.Stream;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamEventMonitorTest {
    @Test
    void monitoringPublishesApplicationEventsInsteadOfAggregateSdkTypes() throws Exception {
        var scheduler = new ControlledScheduler();
        var observations = new ArrayDeque<List<Stream>>();
        observations.add(List.of());
        observations.add(List.of(stream("42", "Title", "game", 10)));
        var events = new ArrayList<Object>();
        var delivered = new java.util.concurrent.CompletableFuture<StreamStateEvent.Online>();
        try (var manager = com.github.twitch4j.common.util.EventManagerUtils.initializeEventManager(
                com.github.philippheuer.events4j.simple.SimpleEventHandler.class);
             var monitor = new StreamEventMonitor(ids -> observations.removeFirst(), event -> {
                 events.add(event); manager.publish(event);
             }, scheduler)) {
            manager.onEvent(StreamStateEvent.Online.class, delivered::complete);
            monitor.enableStreamEventListener(broadcaster("42"));

            scheduler.runUntil(observations::isEmpty);

            assertThat(events).hasSize(1);
            assertThat(delivered.get(5, TimeUnit.SECONDS)).isSameAs(events.getFirst());
            assertThat(events.getFirst().getClass().getPackageName()).startsWith("com.github.k7t3.tcv.domain.event");
            assertThat(events.getFirst()).isInstanceOfSatisfying(StreamStateEvent.Online.class, event -> {
                assertThat(event.userId()).isEqualTo("42");
                assertThat(event.stream().userId()).isEqualTo("42");
                assertThat(event.stream().title()).isEqualTo("Title");
                assertThat(event.stream().gameId()).isEqualTo("game");
                assertThat(event.stream().viewerCount()).isEqualTo(10);
            });
        }
    }


    @Test
    void replacingOneChannelKeepsOtherChannelUpdatesAndDiscardsItsPreviousResult() throws Exception {
        var scheduler = new ControlledScheduler();
        var owner = new AtomicReference<StreamEventMonitor>();
        var observations = new AtomicInteger();
        var events = new ArrayList<Object>();
        var channel = broadcaster("42");
        var initial = stream("43", "Other title", "game", 10);
        var updated = stream("43", "Other title", "game", 20);
        var stale = stream("42", "Old registration", "game", 99);
        try (var monitor = new StreamEventMonitor(ids -> {
            if (observations.incrementAndGet() == 1) return List.of(initial);
            owner.get().disableStreamEventListenerForId(channel);
            owner.get().enableStreamEventListener(channel);
            return List.of(stale, updated);
        }, events::add, scheduler)) {
            owner.set(monitor);
            monitor.enableStreamEventListeners(List.of(channel, broadcaster("43")));
            scheduler.runUntil(() -> observations.get() == 1);
            events.clear();

            scheduler.runUntil(() -> observations.get() == 2);

            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(StreamStateEvent.Viewers.class);
            var event = (StreamStateEvent.Viewers) events.getFirst();
            assertThat(event.userId()).isEqualTo("43");
        }
    }

    @Test
    void observedLiveMetadataChangesPublishEachMatchingEvent() throws Exception {
        var scheduler = new ControlledScheduler();
        var observations = new ArrayDeque<List<Stream>>();
        observations.add(List.of(stream("42", "Title", "game", 10)));
        observations.add(List.of(stream("42", "New title", "new game", 20)));
        var events = new ArrayList<Object>();
        try (var monitor = new StreamEventMonitor(ids -> observations.removeFirst(), events::add, scheduler)) {
            monitor.enableStreamEventListener(broadcaster("42"));
            scheduler.runUntil(() -> observations.size() == 1);
            events.clear();

            scheduler.runUntil(observations::isEmpty);

            assertThat(events).extracting(Object::getClass).containsExactly(StreamStateEvent.Title.class,
                    StreamStateEvent.Game.class, StreamStateEvent.Viewers.class);
        }
    }

    @Test
    void unregisteringAndRegisteringDuringFetchDiscardsThePreviousRegistrationResult() throws Exception {
        var scheduler = new ControlledScheduler();
        var owner = new AtomicReference<StreamEventMonitor>();
        var observations = new AtomicInteger();
        var events = new ArrayList<Object>();
        var live = stream("42", "Old registration", "game", 10);
        var channel = broadcaster("42");
        try (var monitor = new StreamEventMonitor(ids -> {
            if (observations.incrementAndGet() == 2) {
                owner.get().disableStreamEventListenerForId(channel);
                owner.get().enableStreamEventListener(channel);
                return List.of(live);
            }
            return List.of();
        }, events::add, scheduler)) {
            owner.set(monitor);
            monitor.enableStreamEventListener(channel);

            scheduler.runUntil(() -> observations.get() == 3);

            assertThat(events).isEmpty();
        }
    }

    @Test
    void titleCapitalizationChangesArePublished() throws Exception {
        var scheduler = new ControlledScheduler();
        var observations = new ArrayDeque<List<Stream>>();
        observations.add(List.of(stream("42", "Title", "game", 10)));
        observations.add(List.of(stream("42", "TITLE", "game", 10)));
        var events = new ArrayList<Object>();
        try (var monitor = new StreamEventMonitor(ids -> observations.removeFirst(), events::add, scheduler)) {
            monitor.enableStreamEventListener(broadcaster("42"));
            scheduler.runUntil(() -> observations.size() == 1);
            events.clear();

            scheduler.runUntil(observations::isEmpty);

            assertThat(events).extracting(Object::getClass).containsExactly(StreamStateEvent.Title.class);
        }
    }

    @Test
    void closingFromAnEventObserverStopsTheRemainingEvents() throws Exception {
        var scheduler = new ControlledScheduler();
        var owner = new AtomicReference<StreamEventMonitor>();
        var observations = new ArrayDeque<List<Stream>>();
        observations.add(List.of(stream("42", "Title", "game", 10)));
        observations.add(List.of(stream("42", "New title", "new game", 20)));
        var events = new ArrayList<Object>();
        try (var monitor = new StreamEventMonitor(ids -> observations.removeFirst(), event -> {
            events.add(event);
            if (event instanceof StreamStateEvent.Title) {
                try {
                    owner.get().close();
                } catch (java.io.IOException failure) {
                    throw new AssertionError(failure);
                }
            }
        }, scheduler)) {
            owner.set(monitor);
            monitor.enableStreamEventListener(broadcaster("42"));
            scheduler.runUntil(() -> observations.size() == 1);
            events.clear();

            scheduler.runUntil(observations::isEmpty);

            assertThat(events).extracting(Object::getClass).containsExactly(StreamStateEvent.Title.class);
        }
    }


    @Test
    void transientFetchFailureKeepsMonitoringAndRetainsTheLastKnownState() throws Exception {
        var scheduler = new ControlledScheduler();
        var observations = new AtomicInteger();
        var events = new ArrayList<Object>();
        var live = stream("42", "Title", "game", 10);
        try (var monitor = new StreamEventMonitor(ids -> {
            return switch (observations.incrementAndGet()) {
                case 1 -> List.of();
                case 2 -> throw new IllegalStateException("temporary upstream failure");
                default -> List.of(live);
            };
        }, events::add, scheduler)) {
            monitor.enableStreamEventListener(broadcaster("42"));

            scheduler.runUntil(() -> observations.get() == 3);

            assertThat(events).extracting(Object::getClass).containsExactly(StreamStateEvent.Online.class);
        }
    }

    @Test
    void closingDuringFetchDoesNotPublishTheLateResult() throws Exception {
        var scheduler = new ControlledScheduler();
        var owner = new AtomicReference<StreamEventMonitor>();
        var observations = new AtomicInteger();
        var events = new ArrayList<Object>();
        var live = stream("42", "Title", "game", 10);
        try (var monitor = new StreamEventMonitor(ids -> {
            if (observations.incrementAndGet() == 1) return List.of();
            try {
                owner.get().close();
            } catch (java.io.IOException failure) {
                throw new AssertionError(failure);
            }
            return List.of(live);
        }, events::add, scheduler)) {
            owner.set(monitor);
            monitor.enableStreamEventListener(broadcaster("42"));

            scheduler.runUntil(() -> observations.get() == 2);

            assertThat(events).isEmpty();
        }
    }

    @Test
    void observedOfflineLiveOfflineTransitionsPublishMatchingEvents() throws Exception {
        var scheduler = new ControlledScheduler();
        var observations = new ArrayDeque<List<Stream>>();
        observations.add(List.of());
        observations.add(List.of(stream("42", "Title", "game", 10)));
        observations.add(List.of());
        var events = new ArrayList<Object>();
        try (var monitor = new StreamEventMonitor(ids -> observations.removeFirst(), events::add, scheduler)) {
            monitor.enableStreamEventListener(broadcaster("42"));

            scheduler.runUntil(observations::isEmpty);

            assertThat(events).extracting(Object::getClass)
                    .containsExactly(StreamStateEvent.Online.class, StreamStateEvent.Offline.class);
        }
    }

    @Test
    void moreThanOneHundredChannelsAreFetchedInSeparateRequests() throws Exception {
        var scheduler = new ControlledScheduler();
        var broadcasters = IntStream.range(0, 101).mapToObj(i -> broadcaster(Integer.toString(i))).toList();
        var requests = new ArrayList<List<String>>();
        try (var monitor = new StreamEventMonitor(ids -> {
            requests.add(List.copyOf(ids));
            return List.of();
        }, event -> {}, scheduler)) {
            monitor.enableStreamEventListeners(broadcasters);

            scheduler.runUntil(() -> requests.size() == 2);

            assertThat(requests).extracting(List::size).containsExactlyInAnyOrder(100, 1);
            assertThat(requests.stream().flatMap(List::stream).toList()).containsExactlyInAnyOrderElementsOf(
                    broadcasters.stream().map(Broadcaster::getUserId).toList());
        }
    }

    private static Broadcaster broadcaster(String id) {
        return new Broadcaster(id, "login" + id, "Name " + id, null, null);
    }

    private static Stream stream(String id, String title, String game, int viewers) {
        var stream = mock(Stream.class);
        when(stream.getUserId()).thenReturn(id);
        when(stream.getUserLogin()).thenReturn("login" + id);
        when(stream.getType()).thenReturn("live");
        when(stream.getStartedAtInstant()).thenReturn(java.time.Instant.parse("2026-10-21T00:00:00Z"));
        when(stream.getTitle()).thenReturn(title);
        when(stream.getGameId()).thenReturn(game);
        when(stream.getViewerCount()).thenReturn(viewers);
        return stream;
    }

    /** 予約された処理を実時間の待機なしで一つずつ進める。Future のキャンセルには実装済みの状態遷移を使う。 */
    private static final class ControlledScheduler extends ScheduledThreadPoolExecutor {
        private final Deque<Runnable> pending = new ArrayDeque<>();

        private ControlledScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (isShutdown()) throw new RejectedExecutionException("scheduler is closed");
            var future = new ScheduledCommand(command, unit.toNanos(delay));
            pending.add(future);
            return future;
        }

        @Override
        public void execute(Runnable command) {
            if (isShutdown()) throw new RejectedExecutionException("scheduler is closed");
            pending.add(command);
        }

        @Override
        public List<Runnable> shutdownNow() {
            var remaining = new ArrayList<>(super.shutdownNow());
            remaining.addAll(pending);
            pending.clear();
            remaining.forEach(task -> {
                if (task instanceof Future<?> future) future.cancel(false);
            });
            return remaining;
        }

        private void runUntil(BooleanSupplier finished) {
            for (var step = 0; step < 100 && !finished.getAsBoolean(); step++) {
                var next = pending.pollFirst();
                assertThat(next).as("scheduled work before the expected observation").isNotNull();
                next.run();
            }
            assertThat(finished.getAsBoolean()).as("expected observation within scheduled work").isTrue();
        }
    }

    private static final class ScheduledCommand extends FutureTask<Void> implements ScheduledFuture<Void> {
        private final long delayNanos;

        private ScheduledCommand(Runnable command, long delayNanos) {
            super(command, null);
            this.delayNanos = delayNanos;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayNanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(delayNanos, other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
