package com.github.k7t3.tcv.app.emoji;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public class EmojiImageCache extends CachedImageStore<String> {

    private final Emoji emoji;

    public EmojiImageCache(Emoji emoji) {
        super(64, Duration.ofHours(1));
        this.emoji = emoji;
    }

    @Override
    protected @Nullable LazyImage loadImage(String key) {
        // 対応する絵文字がないときはnull
        if (!emoji.contains(key)) return null;
        return new LazyImage(() -> emoji.openImageStream(key), 32, 32);
    }
}
