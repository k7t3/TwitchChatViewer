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

package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventProvider;
import com.github.k7t3.tcv.domain.event.EventPublishers;
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

    private final TwitchChannelEventProvider channelEventProvider;
    private final ChatRoomEventProvider chatEventProvider;

    public ChannelRepository(Twitch twitch, EventPublishers publishers) {
        this.twitch = twitch;
        channelEventProvider = new TwitchChannelEventProvider(twitch, publishers);
        chatEventProvider = new ChatRoomEventProvider(twitch, publishers);
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

        loaded = true;

        channelLock.lock();
        try {
            channelEventProvider.listen();
            chatEventProvider.listen();

            var api = twitch.getTwitchAPI();

            var broadcasters = api.getFollowChannelOwners();
            var userIds = broadcasters.stream().map(Broadcaster::getUserId).toList();
            var streams = api.getStreams(userIds);

            for (var broadcaster : broadcasters) {

                var stream = streams.stream()
                        .filter(s -> s.userId().equalsIgnoreCase(broadcaster.getUserId()))
                        .findFirst()
                        .orElse(null);

                var channel = new TwitchChannel(twitch, broadcaster, stream, chatEventProvider, this);
                channel.setFollowing(true);
                channel.setPersistent(true);
                channels.put(broadcaster.getUserId(), channel);
                channelEventProvider.add(channel);
            }

            // Stream GoLive/GoOffline/GameChange/TitleChange
            channels.values()
                    .stream()
                    .map(TwitchChannel::getBroadcaster)
                    .forEach(api::enableStreamEventListener); // TODO 一括

        } finally {
            channelLock.unlock();
        }
    }

    public List<TwitchChannel> getOrLoadChannels(Collection<String> userIds) {
        LOGGER.info("load channels ({})", userIds);
        if (!loaded) throw new IllegalStateException("not loaded yet");
        if (userIds.isEmpty()) {
            return List.of();
        }

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

                var channel = new TwitchChannel(twitch, broadcaster, stream, chatEventProvider, this);
                channels.put(broadcaster.getUserId(), channel);

                // Stream 監視イベントを有効化
                api.enableStreamEventListener(channel.getBroadcaster());

                channelEventProvider.add(channel);

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

            channel = new TwitchChannel(twitch, broadcaster, stream, chatEventProvider, this);
            channels.put(broadcaster.getUserId(), channel);

            // Stream 監視イベントを有効化
            api.enableStreamEventListener(channel.getBroadcaster());

            channelEventProvider.add(channel);

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

        channelLock.lock();
        try {
            channels.remove(channel.getBroadcaster().getUserId());

            // Stream 監視イベントを無効化
            api.disableStreamEventListener(channel.getBroadcaster());

            // チャンネルに関するイベントの発行を停止
            channelEventProvider.remove(channel);

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
                channel.leaveChat();
                api.disableStreamEventListener(channel.getBroadcaster());

                // チャンネルに関するイベントの発行を停止
                channelEventProvider.remove(channel);
            }

            channels.clear();
            channelEventProvider.clean();
            chatEventProvider.clean();
        } finally {
            channelLock.unlock();
        }
    }

}
