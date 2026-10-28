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

package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.StreamStateUpdateEvent;
import com.github.k7t3.tcv.domain.event.channel.TwitchChannelEvent;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ロードされた{@link TwitchChannelViewModel}インスタンスを管理するリポジトリ
 */
public class ChannelViewModelRepository extends AbstractViewModel {

    /**
     * ユーザーIDとチャンネルのMap
     * FX スレッドで生成・反映・削除する。
     */
    private final Map<String, TwitchChannelViewModel> channelMap = new HashMap<>();

    /**
     * ロードされたチャンネルのリスト
     */
    private final ObservableList<TwitchChannelViewModel> channels = FXCollections.observableArrayList();

    private final ReadOnlyObjectWrapper<ChannelRepository> repository = new ReadOnlyObjectWrapper<>();
    private final ChannelGroupRepository groupRepository;
    private final ImageLoader images;
    private final Executor executor;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private final Consumer<Throwable> failures;
    private final Map<TwitchChannelViewModel, ChannelRepository> owners = new ConcurrentHashMap<>();
    private final Set<FXTask<?>> loads = new HashSet<>();
    private final Map<String, FXTask<TwitchChannelViewModel>> channelLoads = new HashMap<>();
    private FXTask<?> initialLoad;
    private long generation;

    public ChannelViewModelRepository(ChannelGroupRepository groupRepository, ImageLoader images, Executor executor,
                                      Consumer<CompletionStage<Void>> shutdownAwaiter, Consumer<Throwable> failures) {
        this.groupRepository = groupRepository;
        this.images = images;
        this.executor = executor;
        this.shutdownAwaiter = shutdownAwaiter;
        this.failures = failures;
    }

    void trackChatOperation(CompletionStage<Void> operation) { shutdownAwaiter.accept(operation); }

    @Override
    protected void onSubscribeEvents(EventSubscribers eventSubscribers, SessionSubscriptions subscriptions) {
        var onlineSub = subscriber(subscriptions, ChannelOnlineEvent.class, this::onOnlineEvent);
        var offlineSub = subscriber(subscriptions, ChannelOfflineEvent.class, this::onOfflineEvent);
        var stateSub = subscriber(subscriptions, StreamStateUpdateEvent.class, this::onStateUpdated);

        eventSubscribers.subscribeChannelEvent(onlineSub);
        eventSubscribers.subscribeChannelEvent(offlineSub);
        eventSubscribers.subscribeChannelEvent(stateSub);

    }

    private <T extends TwitchChannelEvent> FlowableSubscriber<TwitchChannelEvent> subscriber(
            SessionSubscriptions subscriptions, Class<T> type,
            Consumer<T> consumer
    ) {
        return subscriptions.own(new DownCastFXSubscriber<>(type, consumer));
    }

    private void onOnlineEvent(ChannelOnlineEvent e) {
        var channel = e.getChannel();
        updateChannelState(channel, e.getInfo());
    }

    private void onOfflineEvent(ChannelOfflineEvent e) {
        var channel = e.getChannel();
        updateChannelState(channel, null);
    }

    private void onStateUpdated(StreamStateUpdateEvent e) {
        var channel = e.getChannel();
        updateChannelState(channel, e.getInfo());
    }

    private void updateChannelState(TwitchChannel source, StreamInfo streamInfo) {
        var userId = source.getBroadcaster().getUserId();
        var viewModel = channelMap.get(userId);
        if (viewModel == null || viewModel.getChannel() != source || viewModel.isRetiring()) return;
        viewModel.updateStreamInfo(streamInfo);
    }

    /**
     * リポジトリのすべてのチャンネルをロードする
     */
    public FXTask<?> loadAllAsync() {
        checkOpen();
        if (initialLoad != null) return initialLoad;
        final var repository = Objects.requireNonNull(getRepository());
        final var token = generation;
        var task = load(repository, () -> {
            repository.loadAllRelatedChannels();
            var groups = groupRepository.loadSnapshot();
            var ids = groups.stream().flatMap(group -> group.channelIds().stream()).distinct().toList();
            var loaded = new ArrayList<>(repository.getOrLoadChannels(ids));
            loaded.addAll(repository.getOrLoadChannels());
            return new ChannelSnapshot(groups, List.copyOf(loaded));
        }, snapshot -> {
            for (var channel : snapshot.channels()) {
                requireCurrent(repository, token);
                adoptChannel(repository, channel);
            }
            requireCurrent(repository, token);
            var groupIds = snapshot.groups().stream().flatMap(group -> group.channelIds().stream()).collect(Collectors.toSet());
            for (var id : groupIds) {
                requireCurrent(repository, token);
                var channel = channelMap.get(id);
                if (channel != null) channel.setPersistent(true);
            }
            requireCurrent(repository, token);
            groupRepository.applySnapshot(snapshot.groups(), Map.copyOf(channelMap));
            return null;
        });
        initialLoad = task;
        task.setFinally(() -> { if (initialLoad == task) initialLoad = null; });
        return task;
    }

    private void requireCurrent(ChannelRepository owner, long token) {
        if (isClosed() || generation != token || getRepository() != owner) {
            throw new java.util.concurrent.CancellationException("channel snapshot is no longer owned");
        }
    }

    private record ChannelSnapshot(List<ChannelGroupEntity> groups, List<TwitchChannel> channels) {}

    public ObservableList<TwitchChannelViewModel> getChannels() {
        return channels;
    }

    /**
     * チャンネルをロードする
     * <p>
     *     すでにチャンネルがロードされているときはそれを返す
     * </p>
     * @param broadcaster チャンネルのブロードキャスター
     * @return チャンネル
     */
    public FXTask<TwitchChannelViewModel> getChannelAsync(Broadcaster broadcaster) {
        checkOpen();
        var channel = channelMap.get(broadcaster.getUserId());
        if (channel != null && !channel.isRetiring()) return FXTask.of(channel);
        var pending = channelLoads.get(broadcaster.getUserId());
        if (pending != null && !pending.isDone()) return pending;
        final var repository = Objects.requireNonNull(getRepository());
        var ready = channel == null ? CompletableFuture.<Void>completedStage(null) : channel.retirement();
        var task = load(repository, () -> repository.getOrLoadChannel(broadcaster),
                loaded -> adoptChannel(repository, loaded), ready);
        channelLoads.put(broadcaster.getUserId(), task);
        task.setFinally(() -> channelLoads.remove(broadcaster.getUserId(), task));
        return task;
    }

    private TwitchChannelViewModel adoptChannel(ChannelRepository repository, TwitchChannel channel) {
        var id = channel.getBroadcaster().getUserId();
        var existing = channelMap.get(id);
        if (existing != null && !existing.isRetiring()) return existing;
        if (existing != null) {
            channels.remove(existing);
            owners.remove(existing);
            existing.close();
        }
        var model = new TwitchChannelViewModel(channel, this, images, executor, failures);
        channelMap.put(id, model);
        owners.put(model, repository);
        channels.add(model);
        return model;
    }

    /** 取得はワーカー、返却タスクの成功とモデル生成は FX。取消は両方へ伝播する。 */
    private <R, T> FXTask<T> load(ChannelRepository owner, Callable<R> fetch, Function<R, T> publish) {
        return load(owner, fetch, publish, CompletableFuture.completedStage(null));
    }

    /** ready の実処理終了後に投入する。ワーカー内で前の退出を待つと単一 executor で停止する。 */
    private <R, T> FXTask<T> load(ChannelRepository owner, Callable<R> fetch, Function<R, T> publish,
                                CompletionStage<Void> ready) {
        var token = generation;
        class Operation {
            Callable<T> publication;
            final FXTask<T> result = FXTask.observation(() -> publication.call());
            final FXTask<R> worker = FXTask.task(fetch);

            void finish(Callable<T> action) {
                if (isClosed() || generation != token || getRepository() != owner || result.isCancelled()) {
                    result.cancel();
                    return;
                }
                publication = action;
                result.run();
            }

            void fail(Throwable error) {
                Runnable publication = () -> finish(() -> {
                    Throwable failure = error;
                    while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
                    if (failure instanceof Exception exception) throw exception;
                    if (failure instanceof Error fatal) throw fatal;
                    throw new IllegalStateException(failure);
                });
                if (Platform.isFxApplicationThread()) publication.run();
                else Platform.runLater(publication);
            }
        }
        var operation = new Operation();
        loads.add(operation.result);
        operation.worker.cancelWhenCancelled(operation.result);
        operation.result.setFinally(() -> {
            loads.remove(operation.result);
            operation.worker.cancel();
        });
        operation.worker.onDone(value -> operation.finish(() -> publish.apply(value)));
        operation.worker.setOnFailed(event -> operation.finish(() -> {
            var failure = operation.worker.getException();
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }));
        operation.result.cancelWhenCancelled(operation.worker);
        ready.whenComplete((ignored, failure) -> {
            if (operation.result.isCancelled()) return;
            if (failure != null) { operation.fail(failure); return; }
            try {
                executor.execute(operation.worker);
            } catch (RuntimeException | Error rejected) {
                operation.fail(rejected);
            }
        });
        return operation.result;
    }

    private void checkOpen() {
        if (isClosed()) throw new IllegalStateException("channel repository is closed");
    }

    /**
     * チャンネルをリリースする
     * <p>
     * {@link TwitchChannel#isPersistent()}が有効なら何もしない
     * </p>
     * <p>ドメインの解放が終わるまで呼び出し元で実行する。バックグラウンドから呼び出すこと。
     * 表示リストの更新だけを FX スレッドへ送る。</p>
     */
    public void releaseChannel(TwitchChannelViewModel channel) {
        // 永続化されたチャンネルのときは何もしない
        if (channel.getChannel().isPersistent()) {
            return;
        }

        final var repository = owners.remove(channel);
        if (repository == null) return;
        try {
            repository.releaseChannel(channel.getChannel());
        } finally {
            Platform.runLater(() -> {
                channelMap.remove(channel.getBroadcaster().getUserId(), channel);
                channels.remove(channel);
                channel.close();
            });
        }
    }

    @Override
    protected void onLoggedOut() {
        endSession();
        releaseImages();
    }

    @Override
    protected void onClose() {
        endSession();
        releaseImages();
    }

    private void endSession() {
        generation++;
        repository.set(null);
        owners.clear();
        for (var load : List.copyOf(loads)) load.cancel();
        channelLoads.clear();
        initialLoad = null;
    }

    private void releaseImages() {
        var owned = new HashSet<>(channelMap.values());
        owned.addAll(channels);
        channelMap.clear();
        channels.clear();
        CloseActions.runAll(owned.stream().map(channel -> (Runnable) channel::close).toArray(Runnable[]::new));
    }

    // ==================== PROPERTIES ====================

    public ReadOnlyObjectProperty<ChannelRepository> repositoryProperty() { return repository.getReadOnlyProperty(); }
    public ChannelRepository getRepository() { return repository.get(); }
    public void setRepository(ChannelRepository repository) {
        checkOpen();
        if (getRepository() == repository) return;
        onLoggedOut();
        this.repository.set(repository);
    }

}
