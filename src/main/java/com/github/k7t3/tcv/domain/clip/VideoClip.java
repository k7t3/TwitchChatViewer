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

package com.github.k7t3.tcv.domain.clip;

import com.github.twitch4j.helix.domain.Clip;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record VideoClip(
        String id,
        String url,
        String embeddedUrl,
        String broadcasterId,
        String broadcasterName,
        String creatorId,
        String creatorName,
        String videoId,
        String title,
        int viewCount,
        LocalDateTime createdAt,
        String thumbnailUrl,
        double duration
) {

    public static VideoClip of(Clip clip) {
        var createdAt = LocalDateTime.ofInstant(clip.getCreatedAtInstant(), ZoneId.systemDefault());
        return new VideoClip(
                clip.getId(),
                clip.getUrl(),
                clip.getEmbedUrl(),
                clip.getBroadcasterId(),
                clip.getBroadcasterName(),
                clip.getCreatorId(),
                clip.getCreatorName(),
                clip.getVideoId(),
                clip.getTitle(),
                clip.getViewCount(),
                createdAt,
                clip.getThumbnailUrl(),
                clip.getDuration()
        );
    }

}
