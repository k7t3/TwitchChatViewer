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

package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.event.ClipPostedAppEvent;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.chat.ClipChatMessage;
import com.github.k7t3.tcv.domain.chat.ClipFinder;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomEvent;
import com.github.k7t3.tcv.domain.event.chat.ClipPostedEvent;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 投稿項目と、そのセッションで開始したクリップ照会・画像を所有する。操作・反映は FX スレッドで行う。 */
public class PostedClipRepository extends AbstractViewModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostedClipRepository.class);
    private final ObservableMap<String, PostedClipItem> items = FXCollections.observableHashMap();
    private final ObservableMap<String, PostedClipItem> readOnlyItems = FXCollections.unmodifiableObservableMap(items);
    private final IdentityHashMap<PostedClipItem, Request> requests = new IdentityHashMap<>();
    private final ImageLoader images;
    private final Executor executor;
    private final BiFunction<Twitch, String, VideoClip> lookup;
    private final Consumer<CompletionStage<Void>> shutdownAwaiter;
    private final Consumer<Throwable> failures;
    private final BrowserActions browsers;
    private ClipThumbnailStore thumbnailStore;
    private CachedImageStore<Broadcaster> broadcasterImages;
    private Twitch session;
    private long scope;
    private boolean imagesAvailable = true;

    public PostedClipRepository(ImageLoader images, Executor executor, Consumer<CompletionStage<Void>> shutdownAwaiter,
                                Consumer<Throwable> failures) {
        this(images, executor, (client, url) -> new ClipFinder(client).findClip(url)
                .flatMap(ClipChatMessage::getClip).orElse(null), shutdownAwaiter, failures);
    }

    PostedClipRepository(ImageLoader images, Executor executor, BiFunction<Twitch, String, VideoClip> lookup,
                         Consumer<CompletionStage<Void>> shutdownAwaiter, Consumer<Throwable> failures) {
        this.images = Objects.requireNonNull(images);
        this.executor = Objects.requireNonNull(executor);
        this.lookup = Objects.requireNonNull(lookup);
        this.shutdownAwaiter = Objects.requireNonNull(shutdownAwaiter);
        this.failures = Objects.requireNonNull(failures);
        browsers = new BrowserActions(executor, shutdownAwaiter, failures);
    }

    public void setSession(Twitch twitch) {
        if (isClosed()) throw new IllegalStateException("clip repository is closed");
        Objects.requireNonNull(twitch);
        if (session == twitch) return;
        onLogout();
        session = twitch;
        imagesAvailable = true;
    }

    @Override
    protected void onSubscribeEvents(EventSubscribers eventSubscribers, SessionSubscriptions subscriptions) {
        var subscriber = subscriptions.own(new DownCastFXSubscriber<ChatRoomEvent, ClipPostedEvent>(ClipPostedEvent.class, this::onClipPostedEvent));
        eventSubscribers.subscribeChatEvent(subscriber);
    }

    private void onClipPostedEvent(ClipPostedEvent event) {
        if (isClosed() || !imagesAvailable) return;
        if (session != null && !event.getChatRoom().isOwnedBy(session)) return;
        var message = event.getClipChatMessage();
        var item = items.get(message.getId());
        boolean created = item == null;
        if (item == null) {
            item = new PostedClipItem(this, message.getId(), message.getEstimatedURL(), message.getClip().orElse(null));
            item.onPosted(event.getChatRoom().getBroadcaster());
            items.put(item.getId(), item);
        } else {
            item.onPosted(event.getChatRoom().getBroadcaster());
            if (message.getClip().isPresent() && isActive(item) && items.get(item.getId()) == item) {
                var previous = requests.remove(item);
                if (previous != null) previous.result.cancel(true);
                invalidateThumbnail(item);
                if (isActive(item) && items.get(item.getId()) == item) item.setClip(message.getClip().orElseThrow());
            }
        }
        if (created && message.getClip().isEmpty() && session != null && isActive(item) && items.get(item.getId()) == item) {
            lookup(item, false);
        }
        if (!isClosed() && imagesAvailable) publish(new ClipPostedAppEvent());
    }

    public ObservableMap<String, PostedClipItem> getItems() { return readOnlyItems; }
    public NumberBinding getCountBinding() { return Bindings.size(items); }

    public List<Broadcaster> getPostedBroadcasters() {
        return items.values().stream().flatMap(item -> item.getPostedChannels().stream()).distinct().toList();
    }

    long scope() { return scope; }

    boolean isActive(PostedClipItem item) {
        return !isClosed() && imagesAvailable && !item.isRetired() && item.scope() == scope;
    }

    private void requireActive(PostedClipItem item) {
        if (!isActive(item)) throw new IllegalStateException("clip item is no longer active");
    }

    private void requireOwned(PostedClipItem item) {
        requireActive(item);
        if (items.get(item.getId()) != item) throw new IllegalStateException("clip item is no longer owned");
    }

    FXTask<VideoClip> retry(PostedClipItem item) {
        return lookup(item, true);
    }

    private FXTask<VideoClip> lookup(PostedClipItem item, boolean notifyFailure) {
        requireOwned(item);
        if (session == null) throw new IllegalStateException("clip session is not available");
        var previous = requests.get(item);
        if (previous != null && !previous.result.isDone()) {
            previous.notifyFailure |= notifyFailure;
            return previous.result;
        }
        var request = new Request(item, notifyFailure);
        requests.put(item, request);
        request.worker.cancelWhenCancelled(request.result);
        request.result.cancelWhenCancelled(request.worker);
        request.result.onFailure(failure -> {
            if (!isCurrent(request)) return;
            if (request.notifyFailure) failures.accept(failure);
            else LOGGER.warn("clip lookup failed for {}", request.url, failure);
        });
        request.result.setFinally(() -> {
            requests.remove(item, request);
            request.worker.cancel(true);
        });
        request.worker.onDone(value -> { request.value = value; request.result.run(); });
        request.worker.onFailure(failure -> { request.failure = failure; request.result.run(); });
        try {
            shutdownAwaiter.accept(request.drained.minimalCompletionStage());
            executor.execute(() -> {
                try { request.worker.run(); }
                finally { request.drained.complete(null); }
            });
        } catch (RuntimeException | Error failure) {
            request.failure = failure;
            request.result.run();
            request.worker.cancel(true);
            request.drained.complete(null);
        }
        return request.result;
    }

    private boolean isCurrent(Request request) {
        return isActive(request.item) && items.get(request.item.getId()) == request.item
                && requests.get(request.item) == request && session == request.owner
                && !request.result.isCancelled();
    }

    private final class Request {
        final PostedClipItem item;
        final Twitch owner = session;
        final String url;
        final CompletableFuture<Void> drained = new CompletableFuture<>();
        final FXTask<VideoClip> worker;
        final FXTask<VideoClip> result;
        VideoClip value;
        Throwable failure;
        boolean notifyFailure;

        Request(PostedClipItem item, boolean notifyFailure) {
            this.item = item;
            this.notifyFailure = notifyFailure;
            url = item.getUrl();
            worker = FXTask.task(() -> lookup.apply(owner, url));
            result = FXTask.observation(() -> {
                if (!isCurrent(this)) throw new CancellationException("clip lookup is no longer owned");
                if (failure instanceof Error error) throw error;
                if (failure instanceof Exception exception) throw exception;
                if (value != null) {
                    invalidateThumbnail(item);
                    if (!isCurrent(this)) throw new CancellationException("clip lookup was retired during image release");
                    item.setClip(value);
                }
                return value;
            });
        }
    }

    FXTask<Boolean> browse(PostedClipItem item) {
        requireOwned(item);
        return browsers.open(item.getUrl());
    }

    void remove(PostedClipItem item) {
        if (items.get(item.getId()) != item) return;
        item.retire();
        var request = requests.remove(item);
        CloseActions.runAll(
                () -> { if (request != null) request.result.cancel(true); },
                () -> items.remove(item.getId(), item),
                () -> invalidateThumbnail(item));
    }

    private void invalidateThumbnail(PostedClipItem item) {
        if (thumbnailStore != null) thumbnailStore.invalidate(item);
    }

    LazyImage getThumbnailImage(PostedClipItem item) {
        requireActive(item);
        if (thumbnailStore == null) thumbnailStore = new ClipThumbnailStore(images);
        return thumbnailStore.get(item);
    }

    /** 30px の投稿先プロフィール画像。読込はこのリポジトリが所有する。 */
    public LazyImage getBroadcasterImage(Broadcaster broadcaster) {
        if (isClosed() || !imagesAvailable) throw new IllegalStateException("clip image scope is closed");
        if (broadcasterImages == null) {
            broadcasterImages = new CachedImageStore<>(64, Duration.ofMinutes(5)) {
                @Override protected LazyImage loadImage(Broadcaster key) {
                    return images.load(key.getProfileImageUrl(), 30, 30);
                }
            };
        }
        return broadcasterImages.get(broadcaster);
    }

    @Override protected void onLoggedOut() { endScope(); }
    @Override protected void onClose() { CloseActions.runAll(this::endScope, browsers::close); }

    private void endScope() {
        scope++;
        session = null;
        imagesAvailable = false;
        items.values().forEach(PostedClipItem::retire);
        var pending = List.copyOf(requests.values());
        requests.clear();
        CloseActions.runAll(
                () -> CloseActions.runAll(pending.stream().map(request -> (Runnable) () -> request.result.cancel(true)).toArray(Runnable[]::new)),
                browsers::cancelAll,
                items::clear,
                this::releaseImages);
    }

    private void releaseImages() {
        var thumbnails = thumbnailStore;
        var broadcasters = broadcasterImages;
        thumbnailStore = null;
        broadcasterImages = null;
        CloseActions.runAll(
                () -> { if (thumbnails != null) thumbnails.close(); },
                () -> { if (broadcasters != null) broadcasters.close(); });
    }
}
