package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.twitch4j.TwitchClientHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * ロードしたチャンネルの情報を管理するリポジトリ
 */
public class ChannelRepository implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelRepository.class);

    private final Map<Broadcaster, TwitchChannel> channels = new HashMap<>();

    private boolean loaded = false;

    private final Twitch twitch;

    private final ExecutorService eventExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private TwitchClientHelper clientHelper;

    public ChannelRepository(Twitch twitch) {
        this.twitch = twitch;
        clientHelper = twitch.getClient().getClientHelper();
    }

    public Collection<TwitchChannel> getChannels() {
        return Collections.unmodifiableCollection(channels.values());
    }

    public void loadAllFollowBroadcasters() {
        LOGGER.info("load all follows");
        if (loaded) return;

        var collections = new Channels(twitch);

        var broadcasters = collections.getFollowedBroadcasters();
        var userIds = broadcasters.stream().map(Broadcaster::getUserId).toList();
        var streams = collections.getLiveStreams(userIds);

        for (var broadcaster : broadcasters) {

            var stream = streams.stream()
                    .filter(s -> s.userId().equalsIgnoreCase(broadcaster.getUserId()))
                    .findFirst()
                    .orElse(null);

            var channel = new TwitchChannel(twitch, eventExecutor, broadcaster, stream);
            channel.setFollowing(true);
            channel.updateEventSubs();
            channels.put(broadcaster, channel);
        }

        var channelsNames = channels.values()
                .stream()
                .map(TwitchChannel::getBroadcaster)
                .map(Broadcaster::getUserLogin)
                .toList();

        // Stream GoLive/GoOffline/GameChange/TitleChange
        clientHelper.enableStreamEventListener(channelsNames);

        loaded = true;
    }

    public void updateAllEventListeners() {
        var channelsNames = channels.values().stream()
                .map(TwitchChannel::getBroadcaster)
                .map(Broadcaster::getUserLogin)
                .toList();
        clientHelper.disableStreamEventListener(channelsNames);

        // 更新
        clientHelper = twitch.getClient().getClientHelper();

        clientHelper.enableStreamEventListener(channelsNames);

        channels.values().forEach(TwitchChannel::updateEventSubs);
    }

    public TwitchChannel registerBroadcaster(Broadcaster broadcaster, StreamInfo stream) {
        if (!loaded) throw new IllegalStateException("not loaded yet");
        if (stream != null && !stream.userId().equalsIgnoreCase(broadcaster.getUserId()))
            throw new IllegalArgumentException("broadcaster and streamer users are different");

        var channel = channels.get(broadcaster);
        if (channel != null) {
            LOGGER.info("return existed value");
            return channel;
        }

        channel = new TwitchChannel(twitch, eventExecutor, broadcaster, stream);
        channels.put(broadcaster, channel);

        // Stream 監視イベントを有効化
        clientHelper.enableStreamEventListener(channel.getChannelName());

        return channel;
    }

    public TwitchChannel registerBroadcaster(Broadcaster broadcaster) {
        LOGGER.info("add channel ({})", broadcaster);
        if (!loaded) throw new IllegalStateException("not loaded yet");

        var channel = channels.get(broadcaster);
        if (channel != null) {
            LOGGER.info("return existed value");
            return channel;
        }

        var col = new Channels(twitch);
        var stream = col.getLiveStream(broadcaster.getUserId()).orElse(null);

        return registerBroadcaster(broadcaster, stream);
    }

    public void releaseChannel(TwitchChannel channel) {
        LOGGER.info("release channel ({})", channel);
        if (!loaded) return;

        // フォローしているチャンネルは開放しない
        if (!channel.isFollowing()) {
            // Stream 監視イベントを無効化
            clientHelper.disableStreamEventListener(channel.getChannelName());

            channels.remove(channel.getBroadcaster());
        }
    }

    @Override
    public void close() {
        eventExecutor.close();
    }
}
