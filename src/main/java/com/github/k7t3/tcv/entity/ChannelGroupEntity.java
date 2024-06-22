package com.github.k7t3.tcv.entity;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record ChannelGroupEntity(
        UUID id,
        String name,
        String comment,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Set<String> channelIds
) {
}
