package com.github.k7t3.tcv.vm.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import javafx.scene.image.Image;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

public abstract class CachedImageStore<K> {

    private final LoadingCache<K, Image> images;

    public CachedImageStore(int maxCount, Duration expire) {
        images = Caffeine.newBuilder()
                .maximumSize(maxCount)
                .expireAfterAccess(expire)
                .build(this::loadImage);
    }

    protected abstract @Nullable Image loadImage(K key);

    public Image get(K key) {
        return images.get(key);
    }

    public Optional<Image> getNullable(K key) {
        return Optional.ofNullable(images.get(key));
    }

}
