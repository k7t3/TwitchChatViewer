package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * ロードしたチャンネルの情報を管理するリポジトリ
 */
public class ChannelRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelRepository.class);

    private final Twitch twitch;

    private final Map<Broadcaster, TwitchChannel> channels = new HashMap<>();

    private boolean loaded = false;

    public ChannelRepository(Twitch twitch) {
        this.twitch = twitch;
    }

    public void loadAllFollowBroadcasters() {
        LOGGER.info("load all follows");
        if (loaded) return;

        var collections = new ChannelCollections(twitch);

        var broadcasters = collections.getFollowedBroadcasters();
        var userIds = broadcasters.stream().map(Broadcaster::getUserId).toList();
        var streams = collections.getLiveStreams(userIds);

        for (var broadcaster : broadcasters) {

            var stream = streams.stream()
                    .filter(s -> s.getUserId().equalsIgnoreCase(broadcaster.getUserId()))
                    .findFirst()
                    .orElse(null);

            var channel = new TwitchChannel(twitch, broadcaster, stream);
            channels.put(broadcaster, channel);
        }

        listen();

        loaded = true;
    }

    public Collection<TwitchChannel> getChannels() {
        return Collections.unmodifiableCollection(channels.values());
    }

    /**
     * チャンネルを対象を監視するイベントを有効にするためのメソッド。
     */
    private void listen() {
        var client = twitch.getClient();
        var helper = client.getClientHelper();

        var channelsNames = channels.keySet().stream()
                .map(Broadcaster::getUserLogin)
                .toList();

        // Stream GoLive/GoOffline/GameChange/TitleChange
        helper.enableStreamEventListener(channelsNames);
    }

    public TwitchChannel getChannel(Broadcaster broadcaster) {
        LOGGER.info("add channel ({})", broadcaster);
        if (!loaded) throw new RuntimeException("require loadAllFollowBroadcasters");

        return channels.get(broadcaster);
    }

    public TwitchChannel registerBroadcaster(Broadcaster broadcaster) {
        LOGGER.info("add channel ({})", broadcaster);
        if (!loaded) throw new RuntimeException("require loadAllFollowBroadcasters");

        var col = new ChannelCollections(twitch);
        var stream = col.getLiveStream(broadcaster.getUserId()).orElseThrow();

        var channel = new TwitchChannel(twitch, broadcaster, stream);

        channels.put(broadcaster, channel);

        return channel;
    }

}
