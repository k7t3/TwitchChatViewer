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
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventProvider;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.lifecycle.CloseActions;
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
            return List.copyOf(channels.values());
        } finally {
            channelLock.unlock();
        }
    }

    /**
     * フォローしているすべてのチャンネルをロードする
     */
    public void loadAllRelatedChannels() {
        LOGGER.info("load all follows");
        channelLock.lock();
        try {
            if (loaded) return;
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

                registerChannel(api, broadcaster, stream, true);
            }
            loaded = true;
        } catch (RuntimeException | Error failure) {
            try {
                clear();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            channelLock.unlock();
        }
    }

    public List<TwitchChannel> getOrLoadChannels(Collection<String> userIds) {
        LOGGER.info("load channels ({})", userIds);
        var list = new ArrayList<TwitchChannel>();
        var loadIds = new ArrayList<String>();

        channelLock.lock();
        try {
            if (!loaded) throw new IllegalStateException("not loaded yet");
            if (userIds.isEmpty()) return List.of();
            for (var userId : userIds) {
                var channel = channels.get(userId);

                // すでにロードされているインスタンスがあればそれを使用する
                if (channel != null) {
                    list.add(channel);
                    continue;
                }
                loadIds.add(userId);
            }
            if (loadIds.isEmpty()) return list;

            var api = twitch.getTwitchAPI();
            var broadcasters = api.getBroadcasters(loadIds);
            var streams = api.getStreams(loadIds);

            for (var broadcaster : broadcasters) {
                var stream = streams.stream()
                        .filter(s -> s.userId().equalsIgnoreCase(broadcaster.getUserId()))
                        .findFirst()
                        .orElse(null);

                list.add(registerChannel(api, broadcaster, stream, false));
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
        channelLock.lock();
        try {
            if (!loaded) throw new IllegalStateException("not loaded yet");

            var channel = channels.get(broadcaster.getUserId());
            if (channel != null) {
                LOGGER.info("return existed value");
                return channel;
            }

            var api = twitch.getTwitchAPI();
            var stream = api.getStream(broadcaster.getUserId()).orElse(null);

            return registerChannel(api, broadcaster, stream, false);

        } finally {
            channelLock.unlock();
        }
    }

    /** channelLock 内で監視登録まで成功したモデルだけを公開する。 */
    private TwitchChannel registerChannel(TwitchAPI api, Broadcaster broadcaster, StreamInfo stream, boolean following) {
        var channel = new TwitchChannel(twitch, broadcaster, stream, chatEventProvider, this);
        channel.setFollowing(following);
        channel.setPersistent(following);
        try {
            api.enableStreamEventListener(broadcaster);
            channelEventProvider.add(channel);
            channels.put(broadcaster.getUserId(), channel);
            return channel;
        } catch (RuntimeException | Error failure) {
            try {
                CloseActions.runAll(() -> api.disableStreamEventListener(broadcaster),
                        () -> channelEventProvider.remove(channel));
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
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
        channelLock.lock();
        try {
            if (!loaded || channel.isPersistent()) return;
            var id = channel.getBroadcaster().getUserId();
            if (channels.get(id) != channel) return;
            channels.remove(id);
            CloseActions.runAll(
                    channel::dispose,
                    () -> twitch.getTwitchAPI().disableStreamEventListener(channel.getBroadcaster()),
                    () -> channelEventProvider.remove(channel));
        } finally {
            channelLock.unlock();
        }
    }

    /**
     * ロードしているチャンネルをすべてクリアする
     */
    public void clear() {
        channelLock.lock();
        try {
            var owned = List.copyOf(channels.values());
            channels.clear();
            loaded = false;
            var releases = owned.stream().map(channel -> (Runnable) () -> CloseActions.runAll(
                    channel::dispose,
                    () -> twitch.getTwitchAPI().disableStreamEventListener(channel.getBroadcaster())
            )).toArray(Runnable[]::new);
            CloseActions.runAll(channelEventProvider::clean, chatEventProvider::clean,
                    () -> CloseActions.runAll(releases));
        } finally {
            channelLock.unlock();
        }
    }

}
