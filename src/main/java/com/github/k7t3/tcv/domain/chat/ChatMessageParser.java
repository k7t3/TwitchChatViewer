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

import org.jetbrains.annotations.NotNull;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * チャットメッセージのパーサー。
 * <p>
 *     エモートの文字位置を正確に検出するためには
 *     書記素クラスタを考慮して文字を分解する必要がある。
 * </p>
 * <p>
 *     TwitchのIRC実装は標準に基づきUTF-8を使用している。
 *     一方でJavaは文字列の形式にUTF-16を使用するためコードポイントの数に
 *     着目して処理する必要がある。
 * </p>
 */
public class ChatMessageParser {

    private record Emote(String id, int begin, int end) implements Comparable<Emote> {
        @Override
        public int compareTo(@NotNull ChatMessageParser.Emote o) {
            return Integer.compare(begin(), o.begin());
        }
    }

    public ChatMessage parse(String message, String emoteInfo) {
        if (message == null || message.isEmpty()) {
            return new ChatMessage("", List.of(ChatMessageFragment.text("")));
        }

        var fragments = new ArrayList<ChatMessageFragment>();
        var emotes = parseEmotes(emoteInfo);
        var emote = emotes.isEmpty() ? null : emotes.getFirst();
        var emoteIndex = 0;

        var boundary = BreakIterator.getCharacterInstance();
        boundary.setText(message);

        // 文字位置を指定するカーソル
        int cursor = 0;

        // 読み取った文字列を再構成するためのバッファ
        var builder = new StringBuilder();

        int start = boundary.first();
        for (var end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
            // 適切な境界で分解された一文字
            var singleChar = message.substring(start, end);

            if (emote == null) {
                // 文字を構成する先頭のコードポイントを取得
                var codePoint = singleChar.codePointAt(0);
                if (Character.isEmoji(codePoint)) { // 絵文字のとき
                    builder = cleanBufferAsText(fragments, builder);
                    handleEmojiChar(fragments, singleChar, codePoint);
                } else {
                    builder.append(singleChar);
                }
            } else {
                if (cursor < emote.begin()) { // エモートに到達していない間はテキスト・絵文字として扱う
                    // 文字を構成する先頭のコードポイントを取得
                    var codePoint = singleChar.codePointAt(0);
                    if (Character.isEmoji(codePoint)) { // 絵文字のとき
                        builder = cleanBufferAsText(fragments, builder);
                        handleEmojiChar(fragments, singleChar, codePoint);
                    } else {
                        builder.append(singleChar);
                    }
                } else if (cursor == emote.begin()) { // エモートに到達したときはそれまでのバッファをクリア
                    builder = cleanBufferAsText(fragments, builder);
                    builder.append(singleChar);
                } else if (cursor < emote.end()) { // エモートの終了まで
                    builder.append(singleChar);
                } else if (cursor == emote.end()) { // エモートが終了したとき
                    builder.append(singleChar);
                    var name = builder.toString(); // 名前を取得
                    builder = new StringBuilder(); // クリーン
                    var fragment = ChatMessageFragment.emote(emote.id(), name);
                    fragments.add(fragment);
                    // 次のエモートに移動
                    if (emoteIndex + 1 < emotes.size()) {
                        emoteIndex += 1;
                        emote = emotes.get(emoteIndex);
                    } else {
                        emote = null;
                    }
                }
            }

            // コードポイント準拠の文字数
            var charCount = singleChar.codePointCount(0, singleChar.length());
            cursor += charCount;
        }

        cleanBufferAsText(fragments, builder);

        return new ChatMessage(message, fragments);
    }

    private void handleEmojiChar(List<ChatMessageFragment> fragments, String emojiChar, int codePoint) {
        // Unicodeが構成する長さ
        var charLength = emojiChar.length();
        // 絵文字として使用する文字数
        var innerCursor = Character.charCount(codePoint);

        var hexes = new ArrayList<String>();
        hexes.add(Integer.toHexString(codePoint));

        // 残り文字を絵文字の追加情報としてコードポイントを取得する
        while (innerCursor < charLength) {
            codePoint = emojiChar.codePointAt(innerCursor);
            innerCursor += Character.charCount(codePoint); // 文字数分ずらす
            hexes.add(Integer.toHexString(codePoint));
        }

        fragments.add(ChatMessageFragment.emoji(emojiChar, String.join("-", hexes)));
    }

    private StringBuilder cleanBufferAsText(List<ChatMessageFragment> fragments, StringBuilder builder) {
        if (builder.isEmpty()) return builder;
        var fragment = ChatMessageFragment.text(builder.toString());
        fragments.add(fragment);
        return new StringBuilder();
    }

    private List<Emote> parseEmotes(String emoteValue) {
        if (emoteValue == null || emoteValue.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Emote>();
        var emotes = emoteValue.split("/");
        for (var emote : emotes) {
            var emoteSplit = emote.split(":", 2);
            var id = emoteSplit[0];
            var ranges = emoteSplit[1].split(",");
            for (var range : ranges) {
                var rangeSplit = range.split("-", 2);
                var begin = Integer.parseInt(rangeSplit[0]);
                var end = Integer.parseInt(rangeSplit[1]);
                result.add(new Emote(id, begin, end));
            }
        }
        Collections.sort(result);
        return result;
    }
}
