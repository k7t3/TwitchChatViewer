package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.service.CachedImageStore;
import javafx.scene.image.Image;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public class ClipThumbnailStore extends CachedImageStore<PostedClipViewModel> {

    private static final double THUMBNAIL_WIDTH = 128;
    private static final double THUMBNAIL_HEIGHT = 128;

    public ClipThumbnailStore() {
        super(128, Duration.ofMinutes(3));
    }

    @Override
    protected @Nullable Image loadImage(PostedClipViewModel key) {
        return new Image(key.getThumbnailUrl(), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true, true, true);
    }

}
