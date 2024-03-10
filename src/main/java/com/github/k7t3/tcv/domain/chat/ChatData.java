package com.github.k7t3.tcv.domain.chat;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record ChatData(String channelId, String channelName, String msgId,
                       String userId, @Nullable String userDisplayName,
                       String userName, @Nullable String colorCode,
                       List<ChatBadge> badges, ChatMessage message,
                       Instant firedAt, boolean isSystemMessage) {

    public static final String SYSTEM_MESSAGE_ID = "system_message";

    public static ChatData createSystemData(String message) {
        var fragments = List.of(new ChatMessage.MessageFragment(ChatMessage.Type.MESSAGE, message));
        var chatMessage = new ChatMessage(message, fragments);

        return new ChatData(
                "",
                "",
                SYSTEM_MESSAGE_ID,
                "",
                null,
                "",
                null,
                List.of(),
                chatMessage,
                Instant.now(),
                true
        );
    }

}
