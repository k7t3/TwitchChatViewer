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

/**
 * チャットメッセージを構成する部品。
 * @param type 部品が表す種類
 * @param text メッセージのテキスト
 * @param additional 部品に付属する追加情報。
 *                   エモートの場合は識別子を表し、絵文字のときはHexコードを表す。
 */
public record ChatMessageFragment(
        Type type,
        String text,
        String additional
) {

    public static ChatMessageFragment text(String text) {
        return new ChatMessageFragment(Type.TEXT, text, null);
    }

    public static ChatMessageFragment emote(String emote, String name) {
        return new ChatMessageFragment(Type.EMOTE, emote, name);
    }

    public static ChatMessageFragment emoji(String emojiText, String emojiHexCode) {
        return new ChatMessageFragment(Type.EMOJI, emojiText, emojiHexCode);
    }

    public enum Type {
        EMOTE, EMOJI, TEXT
    }

}
