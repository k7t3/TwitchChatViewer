package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public class ClipThumbnailStore extends CachedImageStore<PostedClipItem> {

    private static final double THUMBNAIL_WIDTH = 128;
    private static final double THUMBNAIL_HEIGHT = 128;

    public ClipThumbnailStore() {
        super(128, Duration.ofMinutes(5));
    }

    @Override
    protected @Nullable LazyImage loadImage(PostedClipItem key) {
        var link = key.getThumbnailLink();
        if (link == null) return null;
        return new LazyImage(link, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
    }

}
