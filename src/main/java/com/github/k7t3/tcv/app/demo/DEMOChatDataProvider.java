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

package com.github.k7t3.tcv.app.demo;

import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import net.datafaker.Faker;

import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

public class DEMOChatDataProvider {

    private final Faker faker = new Faker(Locale.ENGLISH);

    private DEMOChatDataProvider() {
    }

    private String toDisplayUserName(ChatData c) {
        return c.userName().equals(c.userDisplayName()) ? toUserName() : "Display";
    }

    private String toUserName() {
        return "User";
    }

    private ChatMessage toChatMessage(ChatData c) {
        return getSuitableSentence(c);
    }

    private ChatMessage getSuitableSentence(ChatData c) {
        var message = c.message();

        var fragments = new ArrayList<ChatMessageFragment>();

        for (var fragment : message) {
            if (fragment.type() != ChatMessageFragment.Type.TEXT) {
                // テキストでないときはそのまま使用する
                fragments.add(fragment);
                continue;
            }

            var text = fragment.text();

            // ブランクのときはそのまま使用する
            if (text.isBlank()) {
                fragments.add(fragment);
                continue;
            }

            // テキストの長さに応じて適切なセンテンスに入れ替える
            String sentence;
            if (text.length() < 5) {
                sentence = faker.lorem().word();
            }
            else if (text.length() < 20) {
                sentence = faker.lorem().sentence(2);
            }
            else {
                sentence = faker.lorem().sentence(4);
            }

            fragments.add(ChatMessageFragment.text(sentence));
        }

        return new ChatMessage(
                fragments.stream().map(ChatMessageFragment::text).collect(Collectors.joining()),
                fragments
        );
    }

    private static final DEMOChatDataProvider PROVIDER = new DEMOChatDataProvider();

    public static ChatData provide(ChatData c) {
        return new ChatData(
                c.channelId(),
                c.channelName(),
                c.msgId(),
                c.userId(),
                PROVIDER.toDisplayUserName(c),
                PROVIDER.toUserName(),
                c.colorCode(),
                c.badges(),
                PROVIDER.toChatMessage(c),
                c.firedAt()
        );
    }

}
