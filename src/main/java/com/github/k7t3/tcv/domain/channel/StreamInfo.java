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

package com.github.k7t3.tcv.domain.channel;

import com.github.twitch4j.helix.domain.Stream;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record StreamInfo(
        String userId,
        String gameId,
        String gameName,
        String title,
        List<String> tags,
        int viewerCount,
        LocalDateTime startedAt,
        Duration uptime,
        String language
) {

    public static StreamInfo of(Stream stream) {
        var dateTime = LocalDateTime.ofInstant(stream.getStartedAtInstant(), ZoneId.systemDefault());
        return new StreamInfo(
                stream.getUserId(),
                stream.getGameId(),
                stream.getGameName(),
                stream.getTitle(),
                stream.getTags(),
                stream.getViewerCount(),
                dateTime,
                stream.getUptime(),
                stream.getLanguage()
        );
    }

}
