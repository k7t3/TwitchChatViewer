package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;


/**
 * ロードしたチャンネルの情報を管理するリポジトリ
 * <p>
 *     スレッドセーフ
 * </p>
 */
public class ChannelRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelRepository.class);

    private final Map<String, TwitchChannel> channels = new HashMap<>();

    private final ReentrantLock channelLock = new ReentrantLock(true);

    private boolean loaded = false;

    private final Twitch twitch;

    public ChannelRepository(Twitch twitch) {
        this.twitch = twitch;
    }

    /**
     * ロードしているすべてのチャンネルを取得する
     * @return ロードしているすべてのチャンネル
     */
    public Collection<TwitchChannel> getOrLoadChannels() {
        channelLock.lock();
        try {
            return Collections.unmodifiableCollection(channels.values());
        } finally {
            channelLock.unlock();
        }
    }

    /**
     * フォローしているすべてのチャンネルをロードする
     */
    public void loadAllRelatedChannels() {
        LOGGER.info("load all follows");
        if (loaded) return;

        var api = twitch.getTwitchAPI();

        var broadcasters = api.getFollowChannelOwners();
        var userIds = broadcasters.stream().map(Broadcaster::getUserId).toList();
        var streams = api.getStreams(userIds);

        channelLock.lock();
        try {

            for (var broadcaster : broadcasters) {

                var stream = streams.stream()
                        .filter(s -> s.userId().equalsIgnoreCase(broadcaster.getUserId()))
                        .findFirst()
                        .orElse(null);

                var channel = new TwitchChannel(twitch, broadcaster, stream);
                channel.setFollowing(true);
                channel.setPersistent(true);
                channel.updateEventSubs();
                channels.put(broadcaster.getUserId(), channel);
            }

            // Stream GoLive/GoOffline/GameChange/TitleChange
            channels.values()
                    .stream()
                    .map(TwitchChannel::getBroadcaster)
                    .forEach(api::enableStreamEventListener);

        } finally {
            channelLock.unlock();
        }

        loaded = true;
    }

    public List<TwitchChannel> getOrLoadChannels(Collection<String> userIds) {
        LOGGER.info("load channels ({})", userIds);
        if (!loaded) throw new IllegalStateException("not loaded yet");

        var list = new ArrayList<TwitchChannel>();
        var loadIds = new ArrayList<String>();

        channelLock.lock();
        try {
            for (var userId : userIds) {
                var channel = channels.get(userId);

                // すでにロードされているインスタンスがあればそれを使用する
                if (channel != null) {
                    list.add(channel);
                    continue;
                }
                loadIds.add(userId);
            }

            var api = twitch.getTwitchAPI();
            var broadcasters = api.getBroadcasters(loadIds);
            var streams = api.getStreams(loadIds);

            for (var broadcaster : broadcasters) {
                var stream = streams.stream()
                        .filter(s -> s.userId().equalsIgnoreCase(broadcaster.getUserId()))
                        .findFirst()
                        .orElse(null);

                var channel = new TwitchChannel(twitch, broadcaster, stream);
                channel.updateEventSubs();
                channels.put(broadcaster.getUserId(), channel);

                // Stream 監視イベントを有効化
                api.enableStreamEventListener(channel.getBroadcaster());

                list.add(channel);
            }
        } finally {
            channelLock.unlock();
        }

        return list;
    }

    /**
     * チャンネルをロードする
     * <p>
     *     チャンネルがすでにロードされている場合はそれを返す
     * </p>
     * @param broadcaster ロードするBroadcaster
     * @return チャンネル
     */
    public TwitchChannel getOrLoadChannel(Broadcaster broadcaster) {
        LOGGER.info("load channel ({})", broadcaster);
        if (!loaded) throw new IllegalStateException("not loaded yet");

        channelLock.lock();
        try {

            var channel = channels.get(broadcaster.getUserId());
            if (channel != null) {
                LOGGER.info("return existed value");
                return channel;
            }

            var api = twitch.getTwitchAPI();
            var stream = api.getStream(broadcaster.getUserId()).orElse(null);

            channel = new TwitchChannel(twitch, broadcaster, stream);
            channel.updateEventSubs();

            channels.put(broadcaster.getUserId(), channel);

            // Stream 監視イベントを有効化
            api.enableStreamEventListener(channel.getBroadcaster());

            return channel;

        } finally {
            channelLock.unlock();
        }
    }

    /**
     * 使用済みのチャンネルのリソースを開放する
     * <p>
     *     {@link TwitchChannel#isPersistent()}が有効ならそのインスタンスは破棄されず、
     *     {@link ChannelRepository#getOrLoadChannel(Broadcaster)}はそのインスタンスを返す。
     * </p>
     * @param channel 開放するチャンネル
     */
    public void releaseChannel(TwitchChannel channel) {
        LOGGER.info("release channel ({})", channel);
        if (!loaded) return;

        // 永続化しているチャンネルは開放しない
        if (channel.isPersistent()) return;

        var api = twitch.getTwitchAPI();

        // Stream 監視イベントを無効化
        api.disableStreamEventListener(channel.getBroadcaster());

        channelLock.lock();
        try {
            channels.remove(channel.getBroadcaster().getUserId());
        } finally {
            channelLock.unlock();
        }
    }

    /**
     * ロードしているチャンネルをすべてクリアする
     */
    public void clear() {
        var api = twitch.getTwitchAPI();

        channelLock.lock();
        try {
            for (var channel : channels.values()) {

                channel.clear();
                api.disableStreamEventListener(channel.getBroadcaster());

            }

            channels.clear();
        } finally {
            channelLock.unlock();
        }
    }

}
