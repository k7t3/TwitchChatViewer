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
        String language,
        String thumbnailURL
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
                stream.getLanguage(),
                stream.getThumbnailUrl()
        );
    }

}
