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

package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.CachedImageStore;

import java.time.Duration;

/**
 * チャットエモート画像ストア
 */
public class ChatEmoteStore extends CachedImageStore<String> {

    private static final String CDN_FORMAT = "https://static-cdn.jtvnw.net/emoticons/v2/%s/default/light/1.0";

    public ChatEmoteStore() {
        super(100, Duration.ofMinutes(5));
    }

    @Override
    protected LazyImage loadImage(String key) {
        var uri = CDN_FORMAT.formatted(key);
        return new LazyImage(uri, 0, 0);
    }

}
