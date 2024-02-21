package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.common.exception.UnauthorizedException;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.ChannelSearchResult;
import com.github.twitch4j.helix.domain.ChatBadgeSet;
import com.github.twitch4j.helix.domain.Clip;
import com.github.twitch4j.helix.domain.OutboundFollow;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class TwitchAPI implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchAPI.class);

    private static final long SCHEDULED_DELAY_MINUTES = 210;

    private final Twitch twitch;

    private final Lock lock = new ReentrantLock(true);

    private final AtomicBoolean authorized = new AtomicBoolean(true);

    /**
     * 指定の遅延時間が経過したときにトークンをリフレッシュするためのExecutor。
     *
     * <p>
     *     {@link TwitchAPI#hystrixCommandWrapper(Function)}メソッドでステータスコードを
     *     参照して無効化されていればリフレッシュしているが、Twitch4Jの{@link com.github.twitch4j.TwitchClientHelper}が
     *     正常に機能しなくなる恐れがあるため、経過時間を使って動的にリフレッシュを行うことを目的としている。
     * </p>
     */
    private final ScheduledExecutorService scheduledRefreshExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("Token-Refresh-Scheduler").daemon().factory());

    private ScheduledFuture<?> scheduledRefreshFuture = null;

    public TwitchAPI(Twitch twitch) {
        this.twitch = twitch;
    }

    public List<Broadcaster> getFollowChannelOwners() {
        var broadcasters = getFollowChannelOwners(new ArrayList<>(), null);

        LOGGER.info("got broadcasters count {}", broadcasters.size());

        return broadcasters;
    }

    private List<Broadcaster> getFollowChannelOwners(List<Broadcaster> list, String cursor) {
        var set = hystrixCommandWrapper(helix -> helix.getFollowedChannels(
                twitch.getAccessToken(),
                twitch.getUserId(),
                null,
                100,
                cursor)
        );

        var follows = set.getFollows();
        if (follows == null) {
            return list;
        }

        var userIds = follows.stream().map(OutboundFollow::getBroadcasterId).toList();
        var broadcasters = getBroadcasters(userIds);
        list.addAll(broadcasters);

        if (set.getPagination().getCursor() != null) {
            return getFollowChannelOwners(list, set.getPagination().getCursor());
        }

        return list;
    }

    public List<FoundChannel> search(String keyword, boolean liveOnly) {
        var locale = Locale.getDefault();
        var lang = locale.getLanguage();

        var query = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        var command = hystrixCommandWrapper(helix -> helix.searchChannels(
                twitch.getAccessToken(),
                query,
                40,
                null,
                liveOnly)
        );

        var results = command.getResults().stream()
                .sorted((r1, r2) -> {
                    // システムと同じ言語を優先するように並べ替える
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
        var broadcasters = getBroadcasters(userIds);

        var resultIterator = results.iterator();

        return broadcasters.stream().map(b -> {
            assert resultIterator.hasNext();
            var r = resultIterator.next();
            return new FoundChannel(b, r.getIsLive(), r.getGameName());
        }).toList();
    }

    public List<Broadcaster> getBroadcasters(List<String> userIds) {
        if (100 < userIds.size()) {
            throw new IllegalArgumentException("too large userIds");
        }

        var set = hystrixCommandWrapper(helix -> helix.getUsers(
                twitch.getAccessToken(),
                userIds,
                null)
        );

        var users = set.getUsers();
        return users.stream().map(user -> {
            var broadcaster = new Broadcaster(user.getId(), user.getLogin(), user.getDisplayName());
            broadcaster.setProfileImageUrl(user.getProfileImageUrl());
            return broadcaster;
        }).toList();
    }

    public Optional<StreamInfo> getStream(String userId) {
        return getStreams(List.of(userId)).stream().findFirst();
    }

    public List<StreamInfo> getStreams(List<String> userIds) {
        if (100 < userIds.size()) {
            throw new IllegalArgumentException("too large list");
        }

        var limit = Math.clamp(userIds.size(), 1, 100);

        var result = hystrixCommandWrapper(helix -> helix.getStreams(
                twitch.getAccessToken(),
                null,
                null,
                limit,
                null,
                null,
                userIds,
                null)
        );

        return result.getStreams().stream().map(StreamInfo::of).toList();
    }

    public List<ChatBadgeSet> getGlobalBadgeSet() {
        var set = hystrixCommandWrapper(helix -> helix.getGlobalChatBadges(twitch.getAccessToken()));
        return set.getBadgeSets();
    }

    public List<ChatBadgeSet> getChannelBadgeSet(Broadcaster broadcaster) {
        var set = hystrixCommandWrapper(helix -> helix.getChannelChatBadges(
                twitch.getAccessToken(),
                broadcaster.getUserId())
        );
        return set.getBadgeSets();
    }

    public List<Clip> getClips(List<String> clipIds) {
        var list = hystrixCommandWrapper(helix -> helix.getClips(
                twitch.getAccessToken(),
                null,
                null,
                clipIds,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        return list.getData();
    }

    private <T> T hystrixCommandWrapper(Function<TwitchHelix, HystrixCommand<T>> function) {

        lock.lock();

        try {

            var helix = twitch.getClient().getHelix();
            var command = function.apply(helix);
            var result = command.execute();

            // 正常にコマンドを実行できたときはリセットする
            if (scheduledRefreshFuture != null) {
                scheduledRefreshFuture.cancel(true);
            }
            scheduledRefreshFuture = scheduledRefreshExecutor.scheduleWithFixedDelay(
                    this::refreshClient,
                    SCHEDULED_DELAY_MINUTES,
                    SCHEDULED_DELAY_MINUTES,
                    TimeUnit.MINUTES
            );

            return result;

        } catch (HystrixRuntimeException e) {

            if (e.getCause() instanceof UnauthorizedException) {

                // トークンが無効になっていると判断してリフレッシュ
                refreshClient();

                return hystrixCommandWrapper(function);
            }

            LOGGER.error("Hystrix Command Wrapper", e);

            throw new RuntimeException(e);

        } catch (Exception e) {

            throw new RuntimeException(e);

        } finally {

            lock.unlock();

        }
    }

    /**
     * 有効期限付きのアクセストークンを更新するメソッド。
     *
     * <p>
     *     このアプリケーションは認証方法としてDevice Code Flowを採用しているため
     *     定期的にトークンのリフレッシュが必要になるが、Twitch4Jがリフレッシュを
     *     サポートしていないため(Roadmapには登録されている)、使用するトークンを定期的に
     *     更新する必要がある。
     * </p>
     * <p>
     *     {@link com.github.twitch4j.TwitchClient}はトークンを差し替えることができないようなので
     *     リフレッシュ時にインスタンスそのものを入れ替えることで対応する。
     *     この弊害として{@code TwitchClient}を使ってイベントをリッスンしている場合は
     *     すべて付け替えてあげる必要があったり、クラスのプライベートフィールドで
     *     使用している場合はトークンの更新が反映できないため、使用の際はスコープを
     *     細かくしたメソッド引数などで受け取るべきである。
     * </p>
     */
    private void refreshClient() {

        // 既に認証が無効化されているときは何もしない
        if (!authorized.get()) {
            return;
        }

        LOGGER.info("start refresh");
        authorized.set(false);

        try {

            var controller = new CredentialController();
            var credential = controller.refreshToken();

            var credentialManager = controller.getCredentialManager();
            var client = TwitchClientBuilder.builder()
                    .withCredentialManager(credentialManager)
                    .withDefaultAuthToken(credential)
                    .withEnableHelix(true)
                    .build();

            var olderClient = twitch.getClient();

            twitch.setCredential(credential);
            twitch.setClient(client);

            var repository = twitch.getChannelRepository();
            repository.updateAllEventListeners();

            olderClient.close();

        } catch (Exception e) {

            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);

        } finally {

            LOGGER.info("done refresh");
            authorized.set(true);

        }
    }

    @Override
    public void close() throws IOException {
        scheduledRefreshExecutor.close();
    }

}
