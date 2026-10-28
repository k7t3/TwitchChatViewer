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

package com.github.k7t3.tcv.domain.chat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/** チャットの URL 検出のみを行う。API やクライアントを必要としない。 */
public final class ClipLinkParser {
    private static final Pattern CLIP_URL_PATTERN = Pattern.compile(
            "https://((www\\.twitch\\.tv|m\\.twitch\\.tv)/[^/]+/clip|clips\\.twitch\\.tv)/[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']+"
    );

    URI parseClipURI(String chatMessage) {
        var matcher = CLIP_URL_PATTERN.matcher(chatMessage);
        if (!matcher.find()) {
            return null;
        }

        try {
            return new URI(matcher.group().trim());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<ClipChatMessage> parse(String message) {
        var uri = parseClipURI(message);
        if (uri == null) return Optional.empty();
        var paths = uri.getPath().split("/");
        if (paths.length != 4 && paths.length != 2) return Optional.empty();
        return Optional.of(ClipChatMessage.of(paths[paths.length - 1], uri.toString(), message));
    }
}
