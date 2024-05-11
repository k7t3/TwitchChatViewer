package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class ChannelViewModelRepository {

    /**
     * ユーザーIDとチャンネルのMap
     */
    private final ObservableMap<String, TwitchChannelViewModel> channels = FXCollections.observableHashMap();

    private final ChannelRepository repository;

    public ChannelViewModelRepository(ChannelRepository repository) {
        this.repository = repository;
    }

    /**
     * リポジトリのすべてのチャンネルをロードする
     */
    public FXTask<?> loadAllAsync() {
        var task = FXTask.task(() -> {
            // すべての登録しているチャンネルをロードする
            repository.loadAllRelatedChannels();

            return repository.getOrLoadChannels()
                    .stream()
                    .collect(Collectors.toMap(
                            c -> c.getBroadcaster().getUserId(),
                            c -> new TwitchChannelViewModel(c, this)
                    ));
        });
        task.setOnScheduled(e -> channels.clear());
        FXTask.setOnSucceeded(task, e -> {
            channels.putAll(task.getValue());
            channels.values().forEach(this::addListener);
        });
        task.runAsync();
        return task;
    }

    private void addListener(TwitchChannelViewModel channel) {
        // 配信状態を確認するリスナを登録する
        var listener = new TwitchChannelStreamListener(channel);
        channel.getChannel().addListener(listener);
    }

    public List<TwitchChannelViewModel> getFollowingChannels() {
        return channels.values().stream().filter(c -> c.getChannel().isFollowing()).toList();
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
        var channel = channels.get(broadcaster.getUserId());
        if (channel != null) return FXTask.of(channel);

        var t = FXTask.task(() -> {
            var c = repository.getOrLoadChannel(broadcaster);
            var c2 = new TwitchChannelViewModel(c, this);
            // チャンネルにリスナを追加
            addListener(c2);
            return c2;
        });
        t.setSucceeded(() -> channels.put(broadcaster.getUserId(), t.getValue()));
        t.runAsync();
        return t;
    }

    /**
     * チャンネルをロードする
     * <p>
     *     すでにチャンネルがロードされているときはそれを返す
     * </p>
     * @param userIds ユーザーID
     * @return チャンネル
     */
    public FXTask<List<TwitchChannelViewModel>> getChannelsAsync(List<String> userIds) {
        if (userIds.isEmpty()) {
            return FXTask.of(List.of());
        }

        FXTask<List<TwitchChannelViewModel>> t = FXTask.task(() -> {
            var list = new ArrayList<TwitchChannelViewModel>();
            var loadIds = new ArrayList<String>();

            // Channels MapはJavaFX Application Threadでの使用を
            // 想定しているため操作の終了まで待つ必要がある
            var latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                for (var userId : userIds) {
                    var channel = channels.get(userId);
                    if (channel == null) {
                        loadIds.add(userId);
                    } else {
                        list.add(channel);
                    }
                }
                latch.countDown();
            });

            latch.await();

            // すべてのチャンネルがロードされていればそれを返すのみ
            if (loadIds.isEmpty()) {
                return list;
            }

            var channels = repository.getOrLoadChannels(loadIds);
            for (var c : channels) {
                var channel = new TwitchChannelViewModel(c, this);
                // チャンネルにリスナを追加
                addListener(channel);
                list.add(channel);
            }

            return list;
        });
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

        var removed = channels.remove(channel.getBroadcaster().getUserId()) != null;
        if (removed) {
            repository.releaseChannel(channel.getChannel());
        }
    }

    public void clear() {
        channels.clear();
        repository.clear();
    }

}
