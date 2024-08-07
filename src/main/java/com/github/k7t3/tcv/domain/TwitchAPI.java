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
import com.github.twitch4j.common.events.domain.EventChannel;
import com.github.twitch4j.common.exception.UnauthorizedException;
import com.github.twitch4j.common.util.CollectionUtils;
import com.github.twitch4j.common.util.ExponentialBackoffStrategy;
import com.github.twitch4j.domain.ChannelCache;
import com.github.twitch4j.events.*;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.*;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import feign.FeignException;
import io.github.xanthic.cache.api.Cache;
import io.github.xanthic.cache.api.domain.ExpiryType;
import io.github.xanthic.cache.core.CacheApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TwitchAPI implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchAPI.class);

    private final Twitch twitch;

    // Helix APIの多重アクセス防止用のセマフォ
    private final Semaphore semaphore = new Semaphore(4, true);

    // トークンのリフレッシュ時のロック
    private final Lock refreshLock = new ReentrantLock(true);
    private long refreshTimestamp = Long.MIN_VALUE;

    private final ChannelListener channelListener = new ChannelListener();

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
        var query = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        var command = hystrixCommandWrapper(helix -> helix.searchChannels(
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
            var set = hystrixCommandWrapper(helix -> helix.getUsers(
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
            var result = hystrixCommandWrapper(helix -> helix.getStreams(
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
                clipIds.size(),
                null,
                null,
                null
        ));

        return list.getData();
    }

    public boolean enableStreamEventListener(Broadcaster broadcaster) {
        return channelListener.enableStreamEventListener(broadcaster);
    }

    public void enableStreamEventListeners(Iterable<Broadcaster> broadcasters) {
        channelListener.enableStreamEventListeners(broadcasters);
    }

    public boolean disableStreamEventListener(Broadcaster broadcaster) {
        return channelListener.disableStreamEventListenerForId(broadcaster);
    }

    private <T> T hystrixCommandWrapper(Function<TwitchHelix, HystrixCommand<T>> function) {

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            var helix = twitch.getClient().getHelix();
            var command = function.apply(helix);
            return command.execute();

        } catch (HystrixRuntimeException e) {

            if (e.getCause() instanceof UnauthorizedException) {
                // トークンが無効になっていると判断してリフレッシュ
                refreshAccessToken();
                return hystrixCommandWrapper(function);
            }

            if (e.getCause() instanceof TimeoutException) {
                LOGGER.warn(e.getMessage());
                return hystrixCommandWrapper(function);
            }

            if (e.getCause() instanceof FeignException fe) {
                LOGGER.warn(e.getMessage());

                // リトライしてみる
                if (500 <= fe.status() && fe.status() <= 599) {
                    return hystrixCommandWrapper(function);
                }
            }

            if (e.getCause() instanceof InterruptedException) {
                LOGGER.info("interrupted current command");
            } else {
                LOGGER.error("Hystrix Command Wrapper", e);
            }

            throw new RuntimeException(e);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    private void refreshAccessToken() {

        var now = System.nanoTime();
        refreshLock.lock();

        try {
            if (now < refreshTimestamp) {
                LOGGER.info("already refreshed");
                return;
            }

            LOGGER.info("start refresh");

            var controller = new CredentialController(twitch.getCredentialStore());
            var credential = controller.refreshToken();

            // 資格情報がnullのときはリフレッシュできない何らかの事情があるためログアウトする
            if (credential == null) {
                twitch.logout();
                // 無効な資格情報をスロー
                throw new InvalidCredentialException();
            }

            twitch.updateCredential(credential);
            LOGGER.info("done refresh");

            refreshTimestamp = System.nanoTime();

        } catch (InvalidCredentialException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        channelListener.close();
    }

    // TwitchClientHelperより
    private class ChannelListener implements Closeable {

        private static final int MAX_LIMIT = 100;

        /**
         * Holds the channels that are checked for live/offline state changes
         */
        private final Set<String> listenForGoLive = ConcurrentHashMap.newKeySet();

        /**
         * Channel Information Cache
         */
        private final Cache<String, ChannelCache> channelInformation = CacheApi.create(spec -> {
            spec.expiryType(ExpiryType.POST_ACCESS);
            spec.expiryTime(Duration.ofMinutes(10L));
            spec.maxSize(1_048_576L);
        });

        /**
         * Scheduled Thread Pool Executor
         */
        private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                2, Thread.ofPlatform().name("Stream-Watch-Thread").daemon().factory());

        /**
         * Holds the {@link ExponentialBackoffStrategy} used for the stream status listener
         */
        private final AtomicReference<ExponentialBackoffStrategy> liveBackoff;

        /**
         * The {@link Future} associated with streamStatusEventTask, in an atomic wrapper
         */
        private final AtomicReference<Future<?>> streamStatusEventFuture = new AtomicReference<>();

        /**
         * Event Task - Stream Status
         * <p>
         * Accepts a list of channel ids not exceeding {@link ChannelListener#MAX_LIMIT} in size as the input
         */
        private final Consumer<List<String>> streamStatusEventTask;

        public ChannelListener() {
            var defaultBackoff = ExponentialBackoffStrategy.builder().immediateFirst(false).baseMillis(1000L).jitter(false).build();
            this.liveBackoff = new AtomicReference<>(defaultBackoff);

            // Tasks
            this.streamStatusEventTask = channels -> {
                try {
                    Map<String, Stream> streams = new HashMap<>();
                    channels.forEach(id -> streams.put(id, null));

                    // check go live / stream events
                    var streamList = hystrixCommandWrapper(helix ->
                            helix.getStreams(null, null, null, channels.size(), null, null, channels, null));

                    streamList.getStreams().forEach(s -> streams.put(s.getUserId(), s));
                    liveBackoff.get().reset(); // API call was successful

                    streams.forEach((userId, stream) -> {
                        // Check if the channel's live status is still desired to be tracked
                        if (!listenForGoLive.contains(userId))
                            return;

                        ChannelCache currentChannelCache = channelInformation.computeIfAbsent(userId, s -> new ChannelCache());
                        if (stream != null) {
                            // gracefully support name changes
                            currentChannelCache.setUserName(stream.getUserLogin());
                        }
                        final EventChannel channel = new EventChannel(userId, currentChannelCache.getUserName());

                        boolean dispatchGoLiveEvent = false;
                        boolean dispatchGoOfflineEvent = false;
                        boolean dispatchTitleChangedEvent = false;
                        boolean dispatchGameChangedEvent = false;
                        boolean dispatchViewersChangedEvent = false;

                        if (stream != null && stream.getType().equalsIgnoreCase("live")) {
                            // is live
                            // - live status
                            if (currentChannelCache.getIsLive() != null && !currentChannelCache.getIsLive()) {
                                dispatchGoLiveEvent = true;
                            }
                            currentChannelCache.setIsLive(true);
                            boolean wasAlreadyLive = !dispatchGoLiveEvent && currentChannelCache.getIsLive();

                            // - change stream title event
                            if (wasAlreadyLive && currentChannelCache.getTitle() != null && !currentChannelCache.getTitle().equalsIgnoreCase(stream.getTitle())) {
                                dispatchTitleChangedEvent = true;
                            }
                            currentChannelCache.setTitle(stream.getTitle());

                            // - change game event
                            if (wasAlreadyLive && currentChannelCache.getGameId() != null && !currentChannelCache.getGameId().equals(stream.getGameId())) {
                                dispatchGameChangedEvent = true;
                            }
                            currentChannelCache.setGameId(stream.getGameId());

                            // - change viewer count event
                            if (!stream.getViewerCount().equals(currentChannelCache.getViewerCount().getAndSet(stream.getViewerCount())) && wasAlreadyLive) {
                                dispatchViewersChangedEvent = true;
                            }
                        } else {
                            // was online previously?
                            if (currentChannelCache.getIsLive() != null && currentChannelCache.getIsLive()) {
                                dispatchGoOfflineEvent = true;
                            }

                            // is offline
                            currentChannelCache.setIsLive(false);
                            currentChannelCache.setTitle(null);
                            currentChannelCache.setGameId(null);
                            currentChannelCache.getViewerCount().lazySet(null);
                        }

                        var eventManager = twitch.getClient().getEventManager();

                        // dispatch events
                        // - go live event
                        if (dispatchGoLiveEvent) {
                            eventManager.publish(new ChannelGoLiveEvent(channel, stream));
                        }
                        // - go offline event
                        if (dispatchGoOfflineEvent) {
                            eventManager.publish(new ChannelGoOfflineEvent(channel));
                        }
                        // - title changed event
                        if (dispatchTitleChangedEvent) {
                            eventManager.publish(new ChannelChangeTitleEvent(channel, stream));
                        }
                        // - game changed event
                        if (dispatchGameChangedEvent) {
                            eventManager.publish(new ChannelChangeGameEvent(channel, stream));
                        }
                        // - viewer count changed event
                        if (dispatchViewersChangedEvent) {
                            eventManager.publish(new ChannelViewerCountUpdateEvent(channel, stream));
                        }
                    });
                } catch (Exception ex) {

                    LOGGER.error("Failed to check for Stream Events (Live/Offline/...): " + ex.getMessage());

                    executor.shutdown();

                }
            };
        }

        @Override
        public void close() throws IOException {
            cancel(streamStatusEventFuture);
            executor.close();
        }

        public void enableStreamEventListeners(Iterable<Broadcaster> broadcasters) {
            broadcasters.forEach(b -> {
                if (listenForGoLive.add(b.getUserId())) {
                    channelInformation.computeIfAbsent(b.getUserId(), c -> new ChannelCache(b.getUserName()));
                }
                listenForGoLive.add(b.getUserId());
            });
            startOrStopEventGenerationThread();
        }

        public boolean enableStreamEventListener(Broadcaster broadcaster) {
            var channelId = broadcaster.getUserId();
            var channelName = broadcaster.getUserLogin();

            // add to set
            final boolean add = listenForGoLive.add(channelId);
            if (!add) {
                LOGGER.info("Channel {} already added for Stream Events", channelName);
            } else {
                // initialize cache
                channelInformation.computeIfAbsent(channelId, s -> new ChannelCache(channelName));
            }
            startOrStopEventGenerationThread();
            return add;
        }

        public boolean disableStreamEventListenerForId(Broadcaster broadcaster) {
            var channelId = broadcaster.getUserId();

            // remove from set
            boolean remove = listenForGoLive.remove(channelId);

            // invalidate cache
            if (remove) {
                ChannelCache info = channelInformation.get(channelId);
                if (info != null) {
                    info.setIsLive(null);
                    info.setGameId(null);
                    info.setTitle(null);
                }
            }

            startOrStopEventGenerationThread();
            return remove;
        }

        /**
         * Start or quit the thread, depending on usage
         */
        private void startOrStopEventGenerationThread() {
            // stream status event thread
            updateListener(listenForGoLive::isEmpty, streamStatusEventFuture, this::runRecursiveStreamStatusCheck, liveBackoff);
        }

        /**
         * Performs the "heavy lifting" of starting or stopping a listener
         *
         * @param stopCondition   yields whether the listener should be running
         * @param futureReference the current listener in an atomic wrapper
         * @param startCommand    the command to start the listener
         * @param backoff         the {@link ExponentialBackoffStrategy} for the listener
         */
        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter") // Acceptable as futureReference is only streamStatusEventFuture or followerEventFuture
        private void updateListener(BooleanSupplier stopCondition, AtomicReference<Future<?>> futureReference, Runnable startCommand, AtomicReference<ExponentialBackoffStrategy> backoff) {
            if (stopCondition.getAsBoolean()) {
                // Optimization to avoid obtaining an unnecessary lock
                if (futureReference.get() != null) {
                    Future<?> future = null;
                    synchronized (futureReference) {
                        if (stopCondition.getAsBoolean()) // Ensure conditions haven't changed in the time it took to acquire this lock
                            future = futureReference.getAndSet(null); // Clear out the listener future
                    }

                    // Cancel the future
                    if (future != null) {
                        future.cancel(false);
                        backoff.get().reset(); // Ideally we would decrement to zero over time rather than instantly resetting
                    }
                }
            } else {
                // Optimization to avoid obtaining an unnecessary lock
                if (futureReference.get() == null) {
                    // Must synchronize to prevent race condition where multiple threads could be created
                    synchronized (futureReference) {
                        // Start if not already started
                        if (!stopCondition.getAsBoolean() && futureReference.get() == null)
                            futureReference.set(executor.schedule(startCommand, backoff.get().get(), TimeUnit.MILLISECONDS));
                    }
                }
            }
        }

        /**
         * Initiates the stream status listener execution
         */
        private void runRecursiveStreamStatusCheck() {
            runRecursiveCheck(streamStatusEventFuture, executor, CollectionUtils.chunked(listenForGoLive, MAX_LIMIT), liveBackoff, this::runRecursiveStreamStatusCheck, chunk -> {
                streamStatusEventTask.accept(chunk);
                return false; // treat as always consuming from the api rate-limit
            });
        }

    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private static class ListenerRunnable<T> implements Runnable {
        ScheduledExecutorService executor;
        List<T> channels;
        AtomicReference<Future<?>> futureReference;
        AtomicReference<ExponentialBackoffStrategy> backoff;
        Runnable startCommand;
        Function<T, Boolean> executeSingle;

        public ListenerRunnable(
                ScheduledExecutorService executor,
                List<T> channels,
                AtomicReference<Future<?>> futureReference,
                AtomicReference<ExponentialBackoffStrategy> backoff,
                Runnable startCommand,
                Function<T, Boolean> executeSingle
        ) {
            this.executor = executor;
            this.channels = channels;
            this.futureReference = futureReference;
            this.backoff = backoff;
            this.startCommand = startCommand;
            this.executeSingle = executeSingle;
        }

        @Override
        public void run() {
            if (channels.isEmpty()) {
                // Try again later if the task wasn't cancelled
                if (futureReference.get() != null)
                    synchronized (futureReference) {
                        if (cancel(futureReference)) {
                            backoff.get().reset();
                            futureReference.set(executor.schedule(startCommand, backoff.get().get(), TimeUnit.MILLISECONDS));
                        }
                    }
            } else {
                // Start execution from the first element
                run(0);
            }
        }

        private void run(final int index) {
            // If no api call was made by executeSingle, it will return true. Then, we do not need to add any delay before checking the next channel.
            Boolean skipDelay = executeSingle.apply(channels.get(index));

            // Queue up the next check (if the task hasn't been cancelled)
            if (futureReference.get() != null)
                synchronized (futureReference) {
                    if (cancel(futureReference))
                        futureReference.set(
                                executor.schedule(
                                        index + 1 < channels.size() ? () -> run(index + 1) : startCommand,
                                        skipDelay ? 0 : backoff.get().get(),
                                        TimeUnit.MILLISECONDS
                                )
                        );
                }
        }
    }

    private static boolean cancel(AtomicReference<Future<?>> futureRef) {
        Future<?> future = futureRef.get();
        return future != null && future.cancel(false);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static <T> void runRecursiveCheck(AtomicReference<Future<?>> future, ScheduledExecutorService executor, List<T> units, AtomicReference<ExponentialBackoffStrategy> backoff, Runnable startCommand, Function<T, Boolean> task) {
        if (future.get() != null)
            synchronized (future) {
                if (cancel(future))
                    future.set(
                            executor.submit(
                                    new ListenerRunnable<>(
                                            executor,
                                            units,
                                            future,
                                            backoff,
                                            startCommand,
                                            task
                                    )
                            )
                    );
            }
    }

}
