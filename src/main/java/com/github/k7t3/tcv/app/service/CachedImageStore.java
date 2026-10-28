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

package com.github.k7t3.tcv.app.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.image.LazyImage;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 画像の再利用を管理する。所有者はセッション終了時に閉じる。 */
public abstract class CachedImageStore<K> implements AutoCloseable {

    private final LoadingCache<K, LazyImage> images;
    private final ConcurrentHashMap<K, Set<LazyImage>> pendingImages = new ConcurrentHashMap<>();
    private boolean closed;

    public CachedImageStore(int maxCount, Duration expire) {
        images = Caffeine.newBuilder()
                .maximumSize(maxCount)
                .expireAfterAccess(expire)
                .build(this::loadOwnedImage);
    }

    protected abstract @Nullable LazyImage loadImage(K key);

    private @Nullable LazyImage loadOwnedImage(K key) {
        var image = loadImage(key);
        if (image != null) {
            pendingImages.compute(key, (ignored, pending) -> {
                if (pending == null) pending = ConcurrentHashMap.newKeySet();
                pending.add(image);
                return pending;
            });
            image.completion().whenComplete((result, failure) -> pendingImages.computeIfPresent(key, (ignored, pending) -> {
                pending.remove(image);
                return pending.isEmpty() ? null : pending;
            }));
        }
        return image;
    }

    /**
     * 画像を取得する。対応する画像がない場合は null。
     * @param key 画像の識別子
     * @return 同じキーで再利用する画像
     * @throws IllegalStateException このストアが終了している場合
     */
    public @Nullable LazyImage get(K key) {
        synchronized (images) {
            if (closed) throw new IllegalStateException("image store is closed");
            var image = images.get(key);
            if (closed) {
                invalidate(key);
                throw new IllegalStateException("image store is closed");
            }
            return image;
        }
    }

    public Optional<LazyImage> getNullable(K key) {
        return Optional.ofNullable(get(key));
    }

    /** 指定したキーの画像を解放し、次回の取得で読み直す。 */
    public void invalidate(K key) {
        final Set<LazyImage> owned = new HashSet<>();
        synchronized (images) {
            var pending = pendingImages.remove(key);
            if (pending != null) owned.addAll(pending);
            var cached = images.getIfPresent(key);
            if (cached != null) owned.add(cached);
            images.invalidate(key);
        }
        CloseActions.runAll(owned.stream().map(image -> (Runnable) image::close).toArray(Runnable[]::new));
    }

    /** キャッシュが所有する画像の読込を一度だけ取り消す。表示済みの画像は保持される。 */
    @Override
    public void close() {
        final Runnable[] releases;
        synchronized (images) {
            if (closed) return;
            closed = true;
            var owned = new HashSet<LazyImage>();
            pendingImages.values().forEach(owned::addAll);
            owned.addAll(images.asMap().values());
            pendingImages.clear();
            releases = owned.stream()
                    .map(image -> (Runnable) image::close).toArray(Runnable[]::new);
            images.invalidateAll();
        }
        CloseActions.runAll(releases);
    }

}
