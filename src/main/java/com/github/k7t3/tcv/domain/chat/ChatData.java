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

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record ChatData(String channelId, String channelName, String msgId,
                       String userId, @Nullable String userDisplayName,
                       String userName, @Nullable String colorCode,
                       List<ChatBadge> badges, ChatMessage message,
                       Instant firedAt) {

    public static final String SYSTEM_MESSAGE_ID = "system_message";

    public static ChatData createSystemData(String message) {
        if (message == null || message.isEmpty())
            throw new IllegalArgumentException("message is null or empty");

        var fragments = List.of(ChatMessageFragment.text(message));
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
                Instant.now()
        );
    }

}
