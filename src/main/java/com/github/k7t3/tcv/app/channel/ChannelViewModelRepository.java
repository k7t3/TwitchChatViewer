package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChannelViewModelRepository {

    /**
     * ユーザーIDとチャンネルのMap
     * バックグラウンドスレッドで操作されるMapのためConcurrent
     */
    private final Map<String, TwitchChannelViewModel> channelMap = new ConcurrentHashMap<>();

    /**
     * ロードされたチャンネルのリスト
     */
    private final ObservableList<TwitchChannelViewModel> channels = FXCollections.observableArrayList();

    private final ChannelRepository repository;

    private final ChannelGroupRepository groupRepository;

    private final LiveStateNotificator notificator;

    public ChannelViewModelRepository(ChannelRepository repository, ChannelGroupRepository groupRepository) {
        this.repository = repository;
        this.groupRepository = groupRepository;
        notificator = new LiveStateNotificator();
    }

    public LiveStateNotificator getNotificator() {
        return notificator;
    }

    /**
     * リポジトリのすべてのチャンネルをロードする
     */
    public FXTask<?> loadAllAsync() {
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
            groupChannels.forEach((id, c) -> c.setPersistent(true));
            groupRepository.injectChannels(groupChannels);

            // フォローしているチャンネルを含めてロード
            var valMap = new HashMap<>(groupChannels);
            repository.getOrLoadChannels().forEach(c -> {
                var userId = c.getBroadcaster().getUserId();
                if (!groupChannelIds.contains(userId)) {
                    var viewModel = new TwitchChannelViewModel(c, this);
                    valMap.put(userId, viewModel);
                }
            });
            valMap.values().forEach(this::addListener);

            this.channelMap.putAll(valMap);

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

    private void addListener(TwitchChannelViewModel channel) {
        // 配信状態を確認するリスナを登録する
        var listener = new TwitchChannelStreamListener(channel);
        channel.getChannel().addListener(listener);
        channel.getChannel().addListener(notificator);
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

        var t = FXTask.task(() -> {
            var c = repository.getOrLoadChannel(broadcaster);
            var c2 = new TwitchChannelViewModel(c, this);
            // チャンネルにリスナを追加
            addListener(c2);
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

        var t = FXTask.task(() -> {
            repository.releaseChannel(channel.getChannel());
            channelMap.remove(channel.getBroadcaster().getUserId());
            return channel;
        });
        t.onDone(channels::remove);
        t.runAsync();
    }

    public void clear() {
        channels.clear();
        channelMap.clear();
        repository.clear();
    }

}
