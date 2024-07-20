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
