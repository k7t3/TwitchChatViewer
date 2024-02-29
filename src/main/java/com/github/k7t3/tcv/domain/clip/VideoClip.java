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
