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
import com.github.k7t3.tcv.app.image.LazyImage;
import javafx.scene.image.Image;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

public abstract class CachedImageStore<K> {

    private final LoadingCache<K, LazyImage> images;

    public CachedImageStore(int maxCount, Duration expire) {
        images = Caffeine.newBuilder()
                .maximumSize(maxCount)
                .expireAfterAccess(expire)
                .build(this::loadImage);
    }

    protected abstract @Nullable LazyImage loadImage(K key);

    public LazyImage get(K key) {
        return images.get(key);
    }

    public Optional<LazyImage> getNullable(K key) {
        return Optional.ofNullable(images.get(key));
    }

}
