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

package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.event.LiveNotificationEvent;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.channel.*;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 通知の履歴と共有取得の観測を所有する。実際のチャンネル取得はリポジトリが所有する。 */
public class LiveStateNotificator extends AbstractViewModel {
    private static final int RECORD_COUNT = 40;
    public record LiveStateRecord(TwitchChannelViewModel channel, LocalTime time, boolean live) {}
    private record Notice(TwitchChannel source, ChannelRepository owner, long generation, boolean live) {}

    private final ObservableList<LiveStateRecord> records = FXCollections.observableList(new LinkedList<>());
    private final ObservableList<LiveStateRecord> readOnlyRecords = FXCollections.unmodifiableObservableList(records);
    private final IdentityHashMap<FXTask<TwitchChannelViewModel>, Lookup> lookups = new IdentityHashMap<>();
    private final Map<String, Opening> openings = new HashMap<>();
    private final ChannelViewModelRepository repository;
    private final Consumer<Throwable> failures;
    private long generation;

    public LiveStateNotificator(ChannelViewModelRepository repository, Consumer<Throwable> failures) {
        this.repository = repository;
        this.failures = failures;
    }

    public ObservableList<LiveStateRecord> getRecords() { return readOnlyRecords; }

    public boolean canOpen(LiveStateRecord record) {
        return !isClosed() && record != null && record.live() && repository.getRepository() != null
                && records.stream().anyMatch(current -> current == record);
    }

    /** 履歴の旧モデルを使わず、現在の取得元から取得してチャットを開く。 */
    public FXTask<TwitchChannelViewModel> openChannelAsync(LiveStateRecord record) {
        if (!canOpen(record)) {
            var cancelled = FXTask.<TwitchChannelViewModel>observation(() -> null);
            cancelled.cancel(false);
            return cancelled;
        }
        var broadcaster = record.channel().getBroadcaster();
        var existing = openings.get(broadcaster.getUserId());
        if (existing != null && !existing.result.isDone()) return existing.result;
        var opening = new Opening(broadcaster.getUserId(), repository.getRepository(), generation);
        openings.put(opening.id, opening);
        opening.result.setFinally(opening::detach);
        try {
            var task = repository.getChannelAsync(broadcaster);
            if (!opening.isCurrent()) { opening.result.cancel(false); return opening.result; }
            var lookup = lookup(task);
            opening.lookup = lookup;
            lookup.openings.add(opening);
            lookup.attach();
            opening.result.cancelWhenCancelled(task);
        } catch (RuntimeException | Error failure) {
            opening.complete(null, failure);
            if (opening.isCurrent()) failures.accept(failure);
        }
        return opening.result;
    }

    private Lookup lookup(FXTask<TwitchChannelViewModel> task) {
        var lookup = lookups.get(task);
        if (lookup == null) { lookup = new Lookup(task); lookups.put(task, lookup); }
        return lookup;
    }

    @Override protected void onSubscribeEvents(EventSubscribers events, SessionSubscriptions subscriptions) {
        generation++;
        var online = subscriptions.own(new DownCastFXSubscriber<TwitchChannelEvent, ChannelOnlineEvent>(ChannelOnlineEvent.class,
                event -> request(event.getChannel(), true)));
        var offline = subscriptions.own(new DownCastFXSubscriber<TwitchChannelEvent, ChannelOfflineEvent>(ChannelOfflineEvent.class,
                event -> request(event.getChannel(), false)));
        events.subscribeChannelEvent(online);
        events.subscribeChannelEvent(offline);
    }

    private boolean isCurrent(Notice notice) {
        return !isClosed() && generation == notice.generation() && repository.getRepository() == notice.owner()
                && notice.source().isActiveIn(notice.owner());
    }

    private void request(TwitchChannel source, boolean live) {
        var notice = new Notice(source, repository.getRepository(), generation, live);
        if (!isCurrent(notice)) return;
        try {
            var task = repository.getChannelAsync(source.getBroadcaster());
            if (!isCurrent(notice)) return;
            var lookup = lookup(task);
            lookup.notices.add(notice);
            lookup.attach();
        } catch (RuntimeException | Error failure) {
            if (isCurrent(notice)) failures.accept(failure);
        }
    }

    private void push(LiveStateRecord record, BooleanSupplier current) {
        if (!current.getAsBoolean()) return;
        while (records.size() >= RECORD_COUNT) {
            if (!current.getAsBoolean()) return;
            records.removeFirst();
        }
        if (!current.getAsBoolean()) return;
        records.add(record);
        if (current.getAsBoolean()) publish(new LiveNotificationEvent(record));
    }

    private void endScope() {
        generation++;
        var owned = List.copyOf(lookups.values());
        var opening = List.copyOf(openings.values());
        lookups.clear();
        openings.clear();
        CloseActions.runAll(() -> CloseActions.runAll(owned.stream().map(lookup -> (Runnable) lookup::close).toArray(Runnable[]::new)),
                () -> CloseActions.runAll(opening.stream().map(request -> (Runnable) () -> request.result.cancel(false)).toArray(Runnable[]::new)), records::clear);
    }
    @Override protected void onLoggedOut() { endScope(); }
    @Override protected void onClose() { endScope(); }

    private final class Lookup implements AutoCloseable {
        final FXTask<TwitchChannelViewModel> task;
        final List<Notice> notices = new ArrayList<>();
        final List<Opening> openings = new ArrayList<>();
        boolean attached;
        final EventHandler<WorkerStateEvent> completed = event -> finish(event.getEventType());
        boolean finished;
        Lookup(FXTask<TwitchChannelViewModel> task) { this.task = task; }
        void attach() {
            if (attached || finished) return;
            attached = true;
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, completed);
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, completed);
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, completed);
            var state = task.getState();
            if (state == Worker.State.SUCCEEDED) finish(WorkerStateEvent.WORKER_STATE_SUCCEEDED);
            else if (state == Worker.State.FAILED) finish(WorkerStateEvent.WORKER_STATE_FAILED);
            else if (state == Worker.State.CANCELLED) finish(WorkerStateEvent.WORKER_STATE_CANCELLED);
        }
        void finish(javafx.event.EventType<?> type) {
            if (finished) return;
            var pending = List.copyOf(notices);
            var opening = List.copyOf(openings);
            close();
            if (type == WorkerStateEvent.WORKER_STATE_FAILED) {
                var failure = task.getException();
                opening.forEach(request -> request.complete(null, failure));
                if (pending.stream().anyMatch(LiveStateNotificator.this::isCurrent) || opening.stream().anyMatch(Opening::isCurrent)) failures.accept(failure);
                return;
            }
            if (type != WorkerStateEvent.WORKER_STATE_SUCCEEDED) {
                opening.forEach(request -> request.result.cancel(false));
                return;
            }
            var channel = task.getValue();
            for (var notice : pending) {
                BooleanSupplier current = () -> isCurrent(notice) && channel != null
                        && channel.getChannel() == notice.source() && !channel.isRetiring();
                if (current.getAsBoolean()) push(new LiveStateRecord(channel, LocalTime.now(), notice.live()), current);
            }
            opening.forEach(request -> request.complete(channel, null));
        }
        @Override public void close() {
            if (finished) return;
            finished = true;
            lookups.remove(task, this);
            task.removeEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, completed);
            task.removeEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, completed);
            task.removeEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, completed);
            notices.clear();
            openings.forEach(request -> request.lookup = null);
            openings.clear();
        }
    }

    private final class Opening {
        final String id;
        final ChannelRepository owner;
        final long token;
        Lookup lookup;
        TwitchChannelViewModel channel;
        Throwable failure;
        final FXTask<TwitchChannelViewModel> result = FXTask.observation(() -> publishOpening());
        Opening(String id, ChannelRepository owner, long token) { this.id = id; this.owner = owner; this.token = token; }
        boolean isCurrent() {
            return !isClosed() && generation == token && repository.getRepository() == owner
                    && openings.get(id) == this && !result.isCancelled();
        }
        TwitchChannelViewModel publishOpening() throws Exception {
            if (!isCurrent()) throw new CancellationException("history opening was retired");
            if (failure instanceof Error error) throw error;
            if (failure instanceof Exception exception) throw exception;
            if (channel == null || channel.isRetiring() || !channel.isLive() || !channel.getChannel().isActiveIn(owner)
                    || !channel.getBroadcaster().getUserId().equals(id)) return null;
            publish(new ChatOpeningEvent(channel));
            return channel;
        }
        void complete(TwitchChannelViewModel value, Throwable failure) {
            channel = value; this.failure = failure; result.run();
        }
        void detach() {
            openings.remove(id, this);
            if (lookup != null) {
                var owned = lookup; lookup = null; owned.openings.remove(this);
                if (owned.notices.isEmpty() && owned.openings.isEmpty()) owned.close();
            }
        }
    }
}
