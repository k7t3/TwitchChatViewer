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
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ChatRoomEventProvider;
import com.github.twitch4j.helix.domain.ChatBadgeSet;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TwitchChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchChannel.class);

    private final Broadcaster broadcaster;
    private final AtomicReference<StreamInfo> streamRef;
    private final Twitch twitch;
    private final ChatRoomEventProvider chatEventProvider;
    private final ChannelRepository repository;

    private List<ChatBadgeSet> badgeSets = null;
    private final AtomicBoolean badgeLoaded = new AtomicBoolean(false);

    private volatile boolean following;
    private volatile boolean persistent;
    private final Object chatLock = new Object();
    private volatile boolean closed;

    TwitchChannel(
            Twitch twitch,
            Broadcaster broadcaster,
            StreamInfo stream,
            ChatRoomEventProvider chatEventProvider,
            ChannelRepository repository
    ) {
        this.twitch = twitch;
        this.broadcaster = broadcaster;
        this.streamRef = new AtomicReference<>(stream);
        this.chatEventProvider = chatEventProvider;
        this.repository = repository;
    }

    /**
     * チャンネルで使用するバッジをロードする
     * <p>
     *     すでにロード済みであった場合は何もしない
     * </p>
     */
    public void loadBadgesIfNotLoaded() {
        synchronized (chatLock) {
            checkOpen();
            if (badgeLoaded.get()) return;
            var api = twitch.getTwitchAPI();
            badgeSets = List.copyOf(api.getChannelBadgeSet(broadcaster));
            badgeLoaded.set(true);
        }
    }

    private volatile ChatRoom chatRoom;

    /**
     * このチャンネルのチャットルームに参加しているかを返す
     * @return このチャンネルのチャットルームに参加しているか
     */
    public boolean isChatJoined() {
        return chatRoom != null;
    }

    /** 通信や参加を行わず、現在のルームの参照だけを取得する。 */
    public Optional<ChatRoom> getJoinedChatRoom() {
        return Optional.ofNullable(chatRoom);
    }

    /**
     * チャットルームに参加する
     * <p>
     *     すでに参加済みであればそのインスタンスを返す
     * </p>
     * @return チャットルーム
     */
    public ChatRoom getOrJoinChatRoom() {
        synchronized (chatLock) {
            checkOpen();
            if (chatRoom != null) return chatRoom;
            LOGGER.info("{} chat room created", getChannelName());
            chatRoom = new ChatRoom(twitch, broadcaster, this);
            chatEventProvider.onJoined(chatRoom);
            return chatRoom;
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("channel is closed");
    }

    /**
     * チャットルームから退出する
     * <p>
     *     参加済みでなければ何もしない
     * </p>
     */
    public void leaveChat() {
        leaveRoom(false);
    }

    /** リポジトリによる解放。以後は同じインスタンスから参加しない。 */
    void dispose() {
        leaveRoom(true);
    }

    private void leaveRoom(boolean dispose) {
        var release = new AtomicBoolean();
        CloseActions.runAll(() -> {
            synchronized (chatLock) {
                if (dispose) closed = true;
                var owned = chatRoom;
                if (owned == null) return;
                if (!isPersistent()) {
                    closed = true;
                    release.set(!dispose);
                }
                chatRoom = null;
                CloseActions.runAll(owned::leave, () -> chatEventProvider.onLeft(owned));
            }
        }, () -> {
            // channelLock を必要とする処理は chatLock の外へ出し、clear とのロック逆転を防ぐ。
            if (release.get()) repository.releaseChannel(this);
        });
    }

    /** SDK イベントの取得元を、I/O や chatLock の待機なしに判定する。 */
    public boolean isActiveIn(ChannelRepository owner) {
        return owner != null && repository == owner && !closed;
    }

    public Broadcaster getBroadcaster() {
        return broadcaster;
    }

    public String getChannelName() {
        return broadcaster.getUserLogin();
    }

    public void setStream(StreamInfo stream) {
        this.streamRef.set(stream);
    }

    public StreamInfo getStream() {
        return streamRef.get();
    }

    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public boolean isStreaming() {
        return streamRef.get() != null;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isPersistent() {
        return persistent;
    }

    /**
     * バッジのURLを取得する
     * <p>
     *     {@link TwitchChannel#loadBadgesIfNotLoaded()}を事前に実行しておく必要がある
     * </p>
     * @param badge URLを取得したいバッジ
     * @return バッジのイメージURL
     */
    public Optional<String> getBadgeUrl(ChatBadge badge) {
        if (!badgeLoaded.get()) throw new IllegalStateException("badges have not been loaded");

        for (var badgeSet : badgeSets) {
            if (!badgeSet.getSetId().equalsIgnoreCase(badge.id())) continue;

            for (var b : badgeSet.getVersions()) {
                if (!b.getId().equalsIgnoreCase(badge.version())) continue;

                return Optional.of(b.getMediumImageUrl());
            }
        }

        return Optional.empty();
    }

    @Override
    public String toString() {
        return "TwitchChannel{" +
                "broadcaster=" + broadcaster +
                ", stream=" + streamRef +
                '}';
    }
}
