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

package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import com.github.twitch4j.common.util.CollectionUtils;
import com.github.twitch4j.helix.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TwitchAPI implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchAPI.class);

    private final Twitch twitch;

    private final TwitchRequestExecutor requests = new TwitchRequestExecutor();

    // トークンのリフレッシュ時のロック
    private final Lock refreshLock = new ReentrantLock(true);
    private long refreshTimestamp = Long.MIN_VALUE;

    private final StreamEventMonitor streamMonitor;
    private final AtomicBoolean invalidCredentials = new AtomicBoolean();

    public TwitchAPI(Twitch twitch) {
        this(twitch, new ScheduledThreadPoolExecutor(2,
                Thread.ofPlatform().name("Stream-Watch-Thread").daemon().factory()));
    }

    // 未使用の scheduler の所有権を引き受け、API の終了時に停止する。
    TwitchAPI(Twitch twitch, ScheduledThreadPoolExecutor scheduler) {
        this.twitch = twitch;
        this.streamMonitor = new StreamEventMonitor(
                ids -> request(helix -> helix.getStreams(
                        twitch.getAccessToken(), null, null, ids.size(), null, null, ids, null)).getStreams(),
                event -> twitch.getClient().getEventManager().publish(event), scheduler);
    }

    public List<Broadcaster> getFollowChannelOwners() {
        var userIds = FollowedChannelPager.load(this::fetchFollowedPage);
        var broadcasters = List.copyOf(getBroadcasters(userIds));

        LOGGER.info("got broadcasters count {}", broadcasters.size());

        return broadcasters;
    }

    private FollowedChannelPager.Page fetchFollowedPage(String cursor) {
        var set = request(helix -> helix.getFollowedChannels(
                twitch.getAccessToken(),
                twitch.getUserId(),
                null,
                100,
                cursor)
        );

        if (set == null || set.getFollows() == null || set.getPagination() == null) {
            throw new IllegalStateException("Invalid followed channels response: missing data or pagination");
        }
        var userIds = new ArrayList<String>();
        for (var follow : set.getFollows()) {
            if (follow == null || follow.getBroadcasterId() == null || follow.getBroadcasterId().isBlank()) {
                throw new IllegalStateException("Invalid followed channels response: missing broadcaster_id");
            }
            userIds.add(follow.getBroadcasterId());
        }
        return new FollowedChannelPager.Page(userIds, set.getPagination().getCursor());
    }

    public List<FoundChannel> search(String keyword, boolean liveOnly) {
        // Feign expects URI encoding; form encoding would turn spaces into literal '+'.
        var query = URLEncoder.encode(keyword, StandardCharsets.UTF_8).replace("+", "%20");

        var command = request(helix -> helix.searchChannels(
                twitch.getAccessToken(),
                query,
                40,
                null,
                liveOnly)
        );

        // システムと同じ言語を優先するように並べ替える
        var locale = Locale.getDefault();
        var lang = locale.getLanguage();
        var results = command.getResults().stream()
                .sorted((r1, r2) -> {
                    var l1 = r1.getBroadcasterLanguage();
                    var l2 = r2.getBroadcasterLanguage();

                    if (l1.equalsIgnoreCase(l2)) {
                        return l1.equalsIgnoreCase(lang) ? 0 : l1.compareTo(l2);
                    }
                    if (l1.equalsIgnoreCase(lang)) {
                        return -1;
                    }
                    if (l2.equalsIgnoreCase(lang)) {
                        return 1;
                    }
                    return l1.compareTo(l2);

                })
                .toList();

        if (results.isEmpty()) return List.of();

        var userIds = results.stream().map(ChannelSearchResult::getId).toList();
        var broadcasters = getBroadcasters(userIds).stream().collect(Collectors.toMap(Broadcaster::getUserId, b -> b));

        return results.stream()
                .filter(r -> broadcasters.containsKey(r.getId()))
                .map(r -> new FoundChannel(broadcasters.get(r.getId()), r.getIsLive(), r.getGameName()))
                .toList();
    }

    public List<Broadcaster> getBroadcasters(List<String> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        var subList = CollectionUtils.chunked(userIds, 100);
        var broadcasters = new ArrayList<Broadcaster>();

        for (var subUserList : subList) {
            var set = request(helix -> helix.getUsers(
                    twitch.getAccessToken(),
                    subUserList,
                    null)
            );
            var users = set.getUsers();
            var list = users.stream().map(user -> new Broadcaster(
                    user.getId(),
                    user.getLogin(),
                    user.getDisplayName(),
                    user.getProfileImageUrl(),
                    user.getOfflineImageUrl())
            ).toList();
            broadcasters.addAll(list);
        }

        return broadcasters;
    }

    // Twitch4Jの生のStreamを取得する
    private List<Stream> getTwitchStreams(List<String> userIds) {
        var subLists = CollectionUtils.chunked(userIds, 100);
        var streams = new ArrayList<Stream>();

        for (var subUserIds : subLists) {
            var limit = Math.min(userIds.size(), 100);
            var result = request(helix -> helix.getStreams(
                    twitch.getAccessToken(),
                    null,
                    null,
                    limit,
                    null,
                    null,
                    subUserIds,
                    null)
            );
            streams.addAll(result.getStreams());
        }

        return streams;
    }

    public Optional<StreamInfo> getStream(String userId) {
        return getStreams(List.of(userId)).stream().findFirst();
    }

    public List<StreamInfo> getStreams(List<String> userIds) {
        return getTwitchStreams(userIds)
                .stream().filter(s -> !s.getType().isEmpty())
                .map(StreamInfo::of)
                .toList();
    }

    public List<ChatBadgeSet> getGlobalBadgeSet() {
        var set = request(helix -> helix.getGlobalChatBadges(twitch.getAccessToken()));
        return set.getBadgeSets();
    }

    public List<ChatBadgeSet> getChannelBadgeSet(Broadcaster broadcaster) {
        var set = request(helix -> helix.getChannelChatBadges(
                twitch.getAccessToken(),
                broadcaster.getUserId())
        );
        return set.getBadgeSets();
    }

    public List<Clip> getClips(List<String> clipIds) {
        var list = request(helix -> helix.getClips(
                twitch.getAccessToken(),
                null,
                null,
                clipIds,
                null,
                null,
                clipIds.size(),
                null,
                null,
                null
        ));

        return list.getData();
    }

    public boolean enableStreamEventListener(Broadcaster broadcaster) {
        return streamMonitor.enableStreamEventListener(broadcaster);
    }

    public void enableStreamEventListeners(Iterable<Broadcaster> broadcasters) {
        streamMonitor.enableStreamEventListeners(broadcasters);
    }

    public boolean disableStreamEventListener(Broadcaster broadcaster) {
        return streamMonitor.disableStreamEventListenerForId(broadcaster);
    }

    private <T> T request(Function<HelixClient, T> function) {
        return requests.execute(() -> {
            checkCredentials();
            return function.apply(twitch.getClient().getHelix());
        }, this::refreshAccessToken);
    }

    private void checkCredentials() {
        if (invalidCredentials.get() || twitch.hasInvalidCredentials()) throw new InvalidCredentialException();
        if (twitch.isClosing() || twitch.isClosed()) throw new CancellationException("client session has ended");
    }

    private void refreshAccessToken() {
        try { refreshCredential(); }
        catch (InvalidCredentialException failure) {
            invalidCredentials.set(true);
            twitch.invalidateCredentials();
            // 監視も更新ロックを使うため、ロックを解放してから終了完了を待つ。
            try { close(); }
            catch (IOException | RuntimeException cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void refreshCredential() {
        var now = System.nanoTime();
        try {
            // 先行する更新は継続し、取り消された呼出元だけを待機から外す。
            refreshLock.lockInterruptibly();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            var cancelled = new CancellationException("token refresh wait interrupted");
            cancelled.initCause(failure);
            throw cancelled;
        }

        try {
            checkCredentials();
            if (now < refreshTimestamp) {
                LOGGER.info("already refreshed");
                return;
            }

            LOGGER.info("start refresh");

            try (var controller = new CredentialController(twitch.getCredentialStore())) {
                var credential = controller.refreshToken();

                if (credential == null) {
                    throw new InvalidCredentialException();
                }
            }
            LOGGER.info("done refresh");

            refreshTimestamp = System.nanoTime();

        } catch (InvalidCredentialException | CancellationException failure) {
            throw failure;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        streamMonitor.close();
    }
}
