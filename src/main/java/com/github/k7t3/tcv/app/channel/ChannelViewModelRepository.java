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
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.StreamStateUpdateEvent;
import com.github.k7t3.tcv.domain.event.channel.TwitchChannelEvent;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ロードされた{@link TwitchChannelViewModel}インスタンスを管理するリポジトリ
 */
public class ChannelViewModelRepository extends AbstractViewModel {

    /**
     * ユーザーIDとチャンネルのMap
     * バックグラウンドスレッドで操作されるMapのためConcurrent
     */
    private final Map<String, TwitchChannelViewModel> channelMap = new ConcurrentHashMap<>();

    /**
     * ロードされたチャンネルのリスト
     */
    private final ObservableList<TwitchChannelViewModel> channels = FXCollections.observableArrayList();

    private final ObjectProperty<ChannelRepository> repository = new SimpleObjectProperty<>();
    private final ChannelGroupRepository groupRepository;
    private final List<FlowableSubscriber<?>> subscribers = new ArrayList<>();

    public ChannelViewModelRepository(ChannelGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        var onlineSub = subscriber(ChannelOnlineEvent.class, this::onOnlineEvent);
        var offlineSub = subscriber(ChannelOfflineEvent.class, this::onOfflineEvent);
        var stateSub = subscriber(StreamStateUpdateEvent.class, this::onStateUpdated);

        eventSubscribers.subscribeChannelEvent(onlineSub);
        eventSubscribers.subscribeChannelEvent(offlineSub);
        eventSubscribers.subscribeChannelEvent(stateSub);

        subscribers.add(onlineSub);
        subscribers.add(offlineSub);
        subscribers.add(stateSub);
    }

    private <T extends TwitchChannelEvent> FlowableSubscriber<TwitchChannelEvent> subscriber(
            Class<T> type,
            Consumer<T> consumer
    ) {
        return new DownCastFXSubscriber<>(type, consumer);
    }

    private void onOnlineEvent(ChannelOnlineEvent e) {
        var channel = e.getChannel();
        var userId = channel.getBroadcaster().getUserId();
        updateChannelState(userId, e.getInfo());
    }

    private void onOfflineEvent(ChannelOfflineEvent e) {
        var channel = e.getChannel();
        var userId = channel.getBroadcaster().getUserId();
        updateChannelState(userId, null);
    }

    private void onStateUpdated(StreamStateUpdateEvent e) {
        var channel = e.getChannel();
        var userId = channel.getBroadcaster().getUserId();
        updateChannelState(userId, e.getInfo());
    }

    private void updateChannelState(String userId, StreamInfo streamInfo) {
        var viewModel = channelMap.get(userId);
        if (viewModel == null) return;
        viewModel.updateStreamInfo(streamInfo);
    }

    /**
     * リポジトリのすべてのチャンネルをロードする
     */
    public FXTask<?> loadAllAsync() {
        final var repository = Objects.requireNonNull(getRepository());

        var task = FXTask.task(() -> {
            // すべての登録しているチャンネルをロードする
            repository.loadAllRelatedChannels();

            // グループに登録しているチャンネルをロード
            var groupChannelIds = groupRepository.retrieveAllChannelIds();
            var groupChannels = repository.getOrLoadChannels(groupChannelIds).stream()
                    .collect(Collectors.toMap(
                            c -> c.getBroadcaster().getUserId(),
                            c -> new TwitchChannelViewModel(c, this)
                    ));
            // グループに関するチャンネルは永続化する
            groupChannels.forEach((id, c) -> c.setPersistent(true));
            // ロードしたチャンネルをグループリポジトリに渡す
            groupRepository.injectChannels(groupChannels);

            // フォローしているチャンネルをViewModelインスタンスとして生成する
            var valMap = new HashMap<>(groupChannels);
            repository.getOrLoadChannels().forEach(c -> {
                var userId = c.getBroadcaster().getUserId();
                if (!groupChannelIds.contains(userId)) {
                    var viewModel = new TwitchChannelViewModel(c, this);
                    valMap.put(userId, viewModel);
                }
            });
            channelMap.putAll(valMap);
            return valMap;
        });
        task.setOnScheduled(e -> {
            channelMap.clear();
            channels.clear();
        });
        task.onDone(map -> channels.addAll(map.values()));
        task.runAsync();
        return task;
    }

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
        var channel = channelMap.get(broadcaster.getUserId());
        if (channel != null) return FXTask.of(channel);

        final var repository = Objects.requireNonNull(getRepository());
        var t = FXTask.task(() -> {
            var c = repository.getOrLoadChannel(broadcaster);
            var c2 = new TwitchChannelViewModel(c, this);
            channelMap.put(broadcaster.getUserId(), c2);
            return c2;
        });
        t.onDone(channels::add);
        t.runAsync();
        return t;
    }

    /**
     * チャンネルをリリースする
     * <p>
     * {@link TwitchChannel#isPersistent()}が有効なら何もしない
     * </p>
     */
    public void releaseChannel(TwitchChannelViewModel channel) {
        // 永続化されたチャンネルのときは何もしない
        if (channel.getChannel().isPersistent()) {
            return;
        }

        final var repository = Objects.requireNonNull(getRepository());
        var t = FXTask.task(() -> {
            repository.releaseChannel(channel.getChannel());
            channelMap.remove(channel.getBroadcaster().getUserId());
            return channel;
        });
        t.onDone(channels::remove);
        t.runAsync();
    }

    @Override
    public void onLogout() {
        subscribers.forEach(FlowableSubscriber::cancel);
        subscribers.clear();
        channels.clear();
        channelMap.clear();

        var repository = getRepository();
        if (repository != null) {
            repository.clear();
            setRepository(null);
        }
    }

    @Override
    public void close() {
        // no-op
    }

    // ==================== PROPERTIES ====================

    public ObjectProperty<ChannelRepository> repositoryProperty() { return repository; }
    public ChannelRepository getRepository() { return repository.get(); }
    public void setRepository(ChannelRepository repository) { this.repository.set(repository); }

}
