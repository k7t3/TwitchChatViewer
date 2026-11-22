/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.twitch4j.common.util.CollectionUtils;
import com.github.twitch4j.common.util.ExponentialBackoffStrategy;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.event.stream.StreamStateEvent;
import com.github.k7t3.tcv.lifecycle.OwnedScheduler;
import com.github.twitch4j.helix.domain.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** 配信状態の取得・変更通知を、監視の実行世代とチャンネル登録ごとの状態で管理する。 */
final class StreamEventMonitor implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamEventMonitor.class);
    private static final int MAX_LIMIT = 100;

    private final Function<List<String>, List<Stream>> fetchStreams;
    private final Consumer<? super StreamStateEvent> eventSink;
    private final ScheduledThreadPoolExecutor executor;
    private final OwnedScheduler ownedExecutor;
    private final Object stateLock = new Object();
    private final Map<String, Watch> watched = new LinkedHashMap<>();
    private final ExponentialBackoffStrategy backoff = ExponentialBackoffStrategy.builder()
            .immediateFirst(false).baseMillis(1000L).jitter(false).build();

    private volatile boolean closed;
    private long generation;
    private ScheduledFuture<?> pending;

    private static final class State {
        Boolean live;
        String title;
        String gameId;
        Integer viewers;
    }
    private record Watch(String userId, State state) {}
    private record PendingEvent(Watch watch, StreamStateEvent value) {}

    private static final class PollCycle {
        private final long generation;
        private final Deque<List<Watch>> batches = new ArrayDeque<>();

        private PollCycle(long generation) {
            this.generation = generation;
        }
    }

    // 未使用の scheduler の所有権を引き受ける。
    StreamEventMonitor(Function<List<String>, List<Stream>> fetchStreams, Consumer<? super StreamStateEvent> publishEvent,
                       ScheduledThreadPoolExecutor executor) {
        this.fetchStreams = Objects.requireNonNull(fetchStreams);
        this.eventSink = Objects.requireNonNull(publishEvent);
        this.executor = Objects.requireNonNull(executor);
        this.ownedExecutor = new OwnedScheduler(executor);
        executor.setRemoveOnCancelPolicy(true);
    }

    /**
     * チャンネルを監視対象に追加する。登録済みの場合は現在の状態を維持する。
     * @param broadcaster 監視するチャンネル
     * @return 新しい登録を作成した場合は true
     * @throws IllegalStateException この監視が終了している場合
     */
    public boolean enableStreamEventListener(Broadcaster broadcaster) {
        synchronized (stateLock) {
            requireOpen();
            var wasEmpty = watched.isEmpty();
            var added = add(broadcaster);
            if (wasEmpty && added) schedule(new PollCycle(++generation), backoff.get());
            return added;
        }
    }

    /**
     * 既存の登録を維持しながら監視対象をまとめて追加する。
     * @param broadcasters 追加するチャンネル
     * @throws IllegalStateException この監視が終了している場合
     */
    public void enableStreamEventListeners(Iterable<Broadcaster> broadcasters) {
        synchronized (stateLock) {
            requireOpen();
            var wasEmpty = watched.isEmpty();
            broadcasters.forEach(this::add);
            if (wasEmpty && !watched.isEmpty()) schedule(new PollCycle(++generation), backoff.get());
        }
    }

    private boolean add(Broadcaster broadcaster) {
        var id = Objects.requireNonNull(broadcaster.getUserId());
        if (watched.containsKey(id)) return false;
        watched.put(id, new Watch(id, new State()));
        return true;
    }

    /**
     * 登録とその状態を破棄し、当該登録の未完了の取得結果を反映対象から外す。
     * @param broadcaster 解除するチャンネル
     * @return 登録が存在していた場合は true
     */
    public boolean disableStreamEventListenerForId(Broadcaster broadcaster) {
        synchronized (stateLock) {
            if (watched.remove(broadcaster.getUserId()) == null) return false;
            if (watched.isEmpty()) {
                generation++;
                cancelPending();
                backoff.reset();
            }
            return true;
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("stream monitor is closed");
    }

    private boolean isCurrent(PollCycle cycle) {
        return !closed && generation == cycle.generation && !watched.isEmpty();
    }

    private void schedule(PollCycle cycle, long delayMillis) {
        pending = executor.schedule(() -> poll(cycle), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void poll(PollCycle cycle) {
        final List<Watch> batch;
        synchronized (stateLock) {
            if (!isCurrent(cycle)) return;
            if (cycle.batches.isEmpty()) {
                cycle.batches.addAll(CollectionUtils.chunked(new ArrayList<>(watched.values()), MAX_LIMIT));
            }
            batch = cycle.batches.removeFirst().stream()
                    .filter(watch -> watched.get(watch.userId()) == watch).toList();
            if (batch.isEmpty()) {
                schedule(cycle, 0);
                return;
            }
        }

        try {
            var response = fetchStreams.apply(batch.stream().map(Watch::userId).toList());
            var streams = new HashMap<String, Stream>();
            response.forEach(stream -> streams.put(stream.getUserId(), stream));
            var events = new ArrayList<PendingEvent>();
            synchronized (stateLock) {
                if (!isCurrent(cycle)) return;
                for (var watch : batch) {
                    // 解除後に同じ ID が登録されても、古い応答を新しい状態へ反映しない。
                    if (watched.get(watch.userId()) != watch) continue;
                    eventsFor(watch, streams.get(watch.userId())).forEach(event ->
                            events.add(new PendingEvent(watch, event)));
                }
                backoff.reset();
            }
            for (var event : events) {
                synchronized (stateLock) {
                    if (!isCurrent(cycle) || watched.get(event.watch().userId()) != event.watch()) continue;
                }
                eventSink.accept(event.value());
            }
        } catch (Exception failure) {
            if (!closed) LOGGER.warn("Failed to check stream events", failure);
        } finally {
            synchronized (stateLock) {
                if (isCurrent(cycle)) schedule(cycle, backoff.get());
            }
        }
    }

    private List<StreamStateEvent> eventsFor(Watch watch, Stream stream) {
        var events = new ArrayList<StreamStateEvent>();
        var state = watch.state();
        if (stream != null && stream.getType().equalsIgnoreCase("live")) {
            var wentOnline = Boolean.FALSE.equals(state.live);
            var wasAlreadyLive = !wentOnline;
            var titleChanged = wasAlreadyLive && state.title != null && !state.title.equals(stream.getTitle());
            var gameChanged = wasAlreadyLive && state.gameId != null && !state.gameId.equals(stream.getGameId());
            var viewersChanged = wasAlreadyLive && !stream.getViewerCount().equals(state.viewers);
            if (wentOnline || titleChanged || gameChanged || viewersChanged) {
                var info = StreamInfo.of(stream);
                if (wentOnline) events.add(new StreamStateEvent.Online(watch.userId(), info));
                if (titleChanged) events.add(new StreamStateEvent.Title(watch.userId(), info));
                if (gameChanged) events.add(new StreamStateEvent.Game(watch.userId(), info));
                if (viewersChanged) events.add(new StreamStateEvent.Viewers(watch.userId(), info));
            }
            state.live = true;
            state.title = stream.getTitle();
            state.gameId = stream.getGameId();
            state.viewers = stream.getViewerCount();
        } else {
            if (Boolean.TRUE.equals(state.live)) events.add(new StreamStateEvent.Offline(watch.userId()));
            state.live = false;
            state.title = null;
            state.gameId = null;
            state.viewers = null;
        }
        return events;
    }

    private void cancelPending() {
        if (pending != null) pending.cancel(false);
        pending = null;
    }

    /**
     * 新規登録と再予約を止め、所有する scheduler を停止する。複数回呼び出せる。
     * 監視スレッド自身からは停止だけを要求し、その他の呼び出し元は終了完了まで待つ。
     */
    @Override
    public void close() throws IOException {
        synchronized (stateLock) {
            closed = true;
            generation++;
            watched.clear();
            cancelPending();
        }
        ownedExecutor.close();
    }
}
