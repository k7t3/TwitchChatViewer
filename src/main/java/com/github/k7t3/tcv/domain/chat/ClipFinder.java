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

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class ClipFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClipFinder.class);

    private final ClipLinkParser parser = new ClipLinkParser();

    private final Twitch twitch;

    public ClipFinder(Twitch twitch) {
        this.twitch = twitch;
    }

    URI parseClipURI(String chatMessage) {
        return parser.parseClipURI(chatMessage);
    }

    /**
     * チャットに投稿されたクリップのURLをパースしてAPIに投げるメソッド
     */
    public Optional<ClipChatMessage> findClip(String message) {
        var parsed = parser.parse(message);
        if (parsed.isEmpty()) return Optional.empty();
        var link = parsed.orElseThrow();
        var clipId = link.getId();
        var uri = link.getEstimatedURL();

        var api = twitch.getTwitchAPI();
        var clips = api.getClips(List.of(clipId));

        if (clips.isEmpty()) {
            LOGGER.warn("clip not found clip_id={} ({})", clipId, uri);
            return Optional.of(ClipChatMessage.of(clipId, uri.toString(), message));
        }

        return Optional.of(ClipChatMessage.of(clipId, uri.toString(), message, VideoClip.of(clips.getFirst())));
    }

}
