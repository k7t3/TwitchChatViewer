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

package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.SerialIoQueue;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** グループの表示と、受付済みの不変な保存要求を所有する。 */
public class ChannelGroupRepository extends AbstractViewModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelGroupRepository.class);
    private final ChannelGroupEntityService service;
    private final SerialIoQueue io;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private final Consumer<Throwable> reportFailure;
    private final Map<ChannelGroup, Plan> plans = new IdentityHashMap<>();
    private final Map<ChannelGroup, State> committed = new IdentityHashMap<>();
    private final Map<TwitchChannelViewModel, Integer> pendingRetention = new IdentityHashMap<>();
    private long scope;
    private long sequence;
    private final ObservableList<ChannelGroup> groups = FXCollections.observableArrayList(
            group -> new Observable[] {group.pinnedProperty(), group.nameProperty(), group.updatedAtProperty()});

    private record State(ChannelGroupEntity entity, Set<TwitchChannelViewModel> channels) {}

    private static final class Plan {
        final ChannelGroup group;
        final UUID id;
        final LocalDateTime created;
        boolean persisted; // I/O キュー内だけで更新する。
        State durable; // I/O キュー内だけで更新し、各結果へ捕捉する。
        Object latestRequest;
        long publishedSequence;
        boolean deleting;
        Plan(ChannelGroup group, State baseline) {
            this.group = group;
            id = group.getId() == null ? UUID.randomUUID() : group.getId();
            created = group.getCreatedAt() == null ? LocalDateTime.now() : group.getCreatedAt();
            persisted = group.getId() != null;
            durable = baseline;
        }
    }

    private record Outcome(Throwable failure, State durable) {}

    public ChannelGroupRepository(ChannelGroupEntityService service, Executor executor,
                           Consumer<CompletionStage<Void>> shutdownAwaiter, Consumer<Throwable> reportFailure) {
        this.service = Objects.requireNonNull(service);
        io = new SerialIoQueue(executor);
        this.shutdownAwaiter = Objects.requireNonNull(shutdownAwaiter);
        this.reportFailure = Objects.requireNonNull(reportFailure);
    }

    /** 背景で、先に受付済みの書込を終えてから読む。UI モデルは参照しない。 */
    public List<ChannelGroupEntity> loadSnapshot() {
        checkOpen();
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("group loading must run outside FX");
        return io.call(() -> service.retrieveAll().stream().map(entity -> new ChannelGroupEntity(
                entity.id(), entity.name(), entity.comment(), entity.pinned(), entity.createdAt(),
                entity.updatedAt(), Set.copyOf(entity.channelIds()))).toList());
    }

    /** FX 上で一括反映する。読取中に編集・作成・削除した同じ表示モデルを古い一覧で置換しない。 */
    public void applySnapshot(List<ChannelGroupEntity> entities, Map<String, TwitchChannelViewModel> channels) {
        checkOpen();
        var local = new HashMap<UUID, Plan>();
        plans.values().forEach(plan -> local.put(plan.id, plan));
        var next = new ArrayList<ChannelGroup>();
        for (var entity : entities) {
            if (local.containsKey(entity.id())) continue;
            var group = new ChannelGroup(entity.id());
            group.setName(entity.name());
            group.setComment(entity.comment());
            group.setPinned(entity.pinned());
            group.setCreatedAt(entity.createdAt());
            group.setUpdatedAt(entity.updatedAt());
            group.getChannels().addAll(entity.channelIds().stream().map(channels::get)
                    .map(Objects::requireNonNull).toList());
            committed.put(group, capture(group, entity.id(), entity.createdAt(), entity.updatedAt()));
            next.add(group);
        }
        // 保存要求のモデルはこのセッション中の正本。未確定の新規作成は自身の成功時に公開する。
        groups.stream().filter(plans::containsKey).forEach(next::add);
        groups.setAll(next);
        committed.keySet().removeIf(group -> !groups.contains(group) && !plans.containsKey(group));
    }

    public ObservableList<ChannelGroup> getAll() { return groups; }

    public FXTask<?> saveAsync(ChannelGroup group) { return submit(group, false); }
    public FXTask<?> removeAsync(ChannelGroup group) { return submit(group, true); }

    private FXTask<Void> submit(ChannelGroup group, boolean remove) {
        checkOpen();
        if (group == null) throw new IllegalArgumentException("group is null");
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("group edits must run on FX");
        var plan = plans.computeIfAbsent(group, value -> new Plan(value, committed.get(value)));
        if (plan.deleting) throw new IllegalStateException("group deletion is pending or complete");
        var updated = group.getId() == null && plan.latestRequest == null ? plan.created : LocalDateTime.now();
        var snapshot = capture(group, plan.id, plan.created, updated);
        if (!remove && snapshot.channels().stream().anyMatch(TwitchChannelViewModel::isRetiring)) {
            throw new IllegalStateException("cannot save a group with a released channel");
        }
        var request = new Object();
        var previousRequest = plan.latestRequest;
        plan.latestRequest = request;
        if (remove) plan.deleting = true;
        var requestScope = scope;
        long requestSequence = ++sequence;
        var actual = new CompletableFuture<Void>();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var result = FXTask.<Void>observation(() -> {
            rethrow(failure.get());
            return null;
        });
        result.onFailure(error -> {
            if (!isClosed() && scope == requestScope) reportFailure.accept(error);
        });
        // 実行開始前から所有する。観測の取消・画面終了でも受付済み SQL は実行する。
        try { shutdownAwaiter.accept(actual.minimalCompletionStage()); }
        catch (RuntimeException | Error rejected) {
            if (plan.latestRequest == request) {
                plan.latestRequest = previousRequest;
                plan.deleting = false;
            }
            failure.set(rejected);
            actual.completeExceptionally(rejected);
            result.run();
            return result;
        }
        io.submit(() -> {
            try {
                if (remove) {
                    if (plan.persisted) service.remove(snapshot.entity());
                    plan.persisted = false;
                    plan.durable = null;
                } else {
                    service.save(plan.persisted ? SaveType.UPDATE : SaveType.INSERT, snapshot.entity());
                    plan.persisted = true;
                    plan.durable = snapshot;
                }
                return new Outcome(null, plan.durable);
            } catch (Throwable error) {
                return new Outcome(error, plan.durable);
            }
        }).whenComplete((outcome, rejected) -> {
            var completed = rejected == null ? outcome : new Outcome(unwrap(rejected), plan.durable);
            var error = completed.failure();
            failure.set(error);
            try {
                if (error != null) LOGGER.error("Failed to persist channel group", error);
                Platform.runLater(() -> {
                    if (!isClosed() && scope == requestScope && plans.get(group) == plan) {
                        finishPublication(plan, request, requestSequence, snapshot, remove, completed, requestScope);
                    }
                });
            } finally {
                try { result.run(); }
                finally {
                    if (error == null) actual.complete(null);
                    else actual.completeExceptionally(error);
                }
            }
        });
        // 監視と実行受付を先に確定する。プロパティ通知から終了処理へ再入しても自己待機しない。
        if (!remove) snapshot.channels().forEach(channel -> {
            if (isCurrent(plan, requestScope)) {
                pendingRetention.merge(channel, 1, Integer::sum);
                reconcileRetention(channel);
            }
        });
        return result;
    }

    private boolean isCurrent(Plan plan, long expectedScope) {
        return !isClosed() && scope == expectedScope && plans.get(plan.group) == plan;
    }

    private void finishPublication(Plan plan, Object request, long order, State snapshot, boolean remove,
                                   Outcome outcome, long requestScope) {
        var affected = new HashSet<>(snapshot.channels());
        var previous = committed.get(plan.group);
        if (previous != null) affected.addAll(previous.channels());
        if (outcome.durable() != null) affected.addAll(outcome.durable().channels());
        try { applyWrite(plan, request, order, snapshot, remove, outcome, requestScope); }
        finally {
            if (isCurrent(plan, requestScope)) {
                if (!remove) snapshot.channels().forEach(channel ->
                        pendingRetention.computeIfPresent(channel, (key, count) -> count == 1 ? null : count - 1));
                affected.forEach(channel -> {
                    if (isCurrent(plan, requestScope)) reconcileRetention(channel);
                });
            }
        }
    }

    private void reconcileRetention(TwitchChannelViewModel channel) {
        boolean retained = channel.isFollowing() || pendingRetention.containsKey(channel)
                || committed.values().stream().anyMatch(state -> state.channels().contains(channel));
        channel.setPersistent(retained);
    }

    private void applyWrite(Plan plan, Object request, long order, State snapshot, boolean remove, Outcome outcome,
                            long requestScope) {
        if (order <= plan.publishedSequence) return;
        plan.publishedSequence = order;
        var group = plan.group;
        var durable = outcome.durable();
        if (outcome.failure() != null) {
            if (plan.latestRequest == request) {
                plan.deleting = false;
                if (durable != null) restoreUnchangedFields(group, snapshot, durable);
            }
        } else if (remove) {
            groups.remove(group);
            if (isCurrent(plan, requestScope)) committed.remove(group);
            return;
        }
        if (!isCurrent(plan, requestScope)) return;
        if (durable != null) {
            group.assignPersistedId(durable.entity().id());
            group.setCreatedAt(durable.entity().createdAt());
            if (!isCurrent(plan, requestScope)) return;
            group.setUpdatedAt(durable.entity().updatedAt());
            if (!isCurrent(plan, requestScope)) return;
            committed.put(group, durable);
            if (!plan.deleting && !groups.contains(group)) groups.add(group);
        }
    }

    private static State capture(ChannelGroup group, UUID id, LocalDateTime created, LocalDateTime updated) {
        var channels = Set.copyOf(group.getChannels());
        var ids = channels.stream().map(channel -> channel.getBroadcaster().getUserId()).collect(Collectors.toUnmodifiableSet());
        return new State(new ChannelGroupEntity(id, group.getName(), group.getComment(), group.isPinned(),
                created, updated, ids), channels);
    }

    private static void restoreUnchangedFields(ChannelGroup group, State requested, State baseline) {
        var input = requested.entity();
        var stored = baseline.entity();
        if (Objects.equals(group.getName(), input.name())) group.setName(stored.name());
        if (Objects.equals(group.getComment(), input.comment())) group.setComment(stored.comment());
        if (group.isPinned() == input.pinned()) group.setPinned(stored.pinned());
        if (Set.copyOf(group.getChannels()).equals(requested.channels())) {
            group.getChannels().clear();
            group.getChannels().addAll(baseline.channels());
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) throw exception;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
    }

    private void checkOpen() { if (isClosed()) throw new IllegalStateException("group repository is closed"); }
    private void endSession() {
        scope++;
        var pending = List.copyOf(pendingRetention.keySet());
        pendingRetention.clear();
        groups.clear();
        plans.clear();
        pending.forEach(this::reconcileRetention);
        committed.clear();
    }
    @Override protected void onLoggedOut() { endSession(); }
    @Override protected void onClose() { endSession(); }
}
