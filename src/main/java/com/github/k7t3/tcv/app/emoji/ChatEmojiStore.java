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

package com.github.k7t3.tcv.app.emoji;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public class ChatEmojiStore extends CachedImageStore<String> {

    private final Emoji emoji;

    public ChatEmojiStore(Emoji emoji) {
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
