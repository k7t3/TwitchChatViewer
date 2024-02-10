package com.github.k7t3.tcv.domain.chat;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ChatData(String channelId, String channelName, String msgId,
                       String userId, @Nullable String userDisplayName,
                       String userName, @Nullable String colorCode,
                       List<ChatBadge> badges, ChatMessage message) {
}
