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
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;

import java.time.Duration;

public class GlobalChatBadgeStore extends CachedImageStore<ChatBadge> {

    private final GlobalChatBadges globalBadges;

    public GlobalChatBadgeStore(GlobalChatBadges globalChatBadges) {
        super(16, Duration.ofMinutes(5));
        this.globalBadges = globalChatBadges;
    }

    @Override
    protected LazyImage loadImage(ChatBadge key) {
        var url = globalBadges.getBadgeUrl(key.id(), key.version()).orElse(null);
        if (url == null) return null;
        return new LazyImage(url, 0, 0);
    }

}
