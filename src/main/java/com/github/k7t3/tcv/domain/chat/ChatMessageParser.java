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

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * チャットメッセージのパーサー。
 * <p>位置タグをコードポイント単位で検証してから、UTF-16の半開区間へ変換する。
 * 書記素を途中で切る範囲、不正な範囲、重複する範囲は本文を保ったまま除外する。
 * 同じ開始位置では先に記載された範囲を優先する。</p>
 * <p>エモート以外の本文は書記素単位でテキストと絵文字へ分割する。</p>
 */
public class ChatMessageParser {

    /** 検証済みエモートのUTF-16半開区間。 */
    private record Emote(String id, int begin, int endExclusive) {}

    public ChatMessage parse(String message, String emoteInfo) {
        if (message == null || message.isEmpty()) {
            return new ChatMessage("", List.of(ChatMessageFragment.text("")));
        }

        var fragments = new ArrayList<ChatMessageFragment>();
        var cursor = 0;
        for (var emote : parseEmotes(emoteInfo, message)) {
            appendText(fragments, message.substring(cursor, emote.begin()));
            fragments.add(ChatMessageFragment.emote(emote.id(), message.substring(emote.begin(), emote.endExclusive())));
            cursor = emote.endExclusive();
        }
        appendText(fragments, message.substring(cursor));
        return new ChatMessage(message, List.copyOf(fragments));
    }

    private void appendText(List<ChatMessageFragment> fragments, String text) {
        if (text.isEmpty()) return;
        var boundary = BreakIterator.getCharacterInstance();
        boundary.setText(text);
        var builder = new StringBuilder();
        int start = boundary.first();
        for (var end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
            var singleChar = text.substring(start, end);
            var codePoint = singleChar.codePointAt(0);
            if (Character.isEmoji(codePoint)) {
                builder = cleanBufferAsText(fragments, builder);
                handleEmojiChar(fragments, singleChar, codePoint);
            } else {
                builder.append(singleChar);
            }
        }
        cleanBufferAsText(fragments, builder);
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

    private List<Emote> parseEmotes(String emoteValue, String message) {
        if (emoteValue == null || emoteValue.isEmpty()) {
            return List.of();
        }
        var offsets = codePointOffsets(message);
        var codePointCount = offsets.length - 1;
        var boundary = BreakIterator.getCharacterInstance();
        boundary.setText(message);
        var result = new ArrayList<Emote>();
        var emotes = emoteValue.split("/");
        for (var emote : emotes) {
            var emoteSplit = emote.split(":", 2);
            if (emoteSplit.length != 2) continue;
            var id = emoteSplit[0];
            if (id.isBlank()) continue;
            var ranges = emoteSplit[1].split(",");
            for (var range : ranges) {
                var rangeSplit = range.split("-", 2);
                if (rangeSplit.length != 2) continue;
                try {
                    var begin = Integer.parseInt(rangeSplit[0]);
                    var end = Integer.parseInt(rangeSplit[1]);
                    if (begin < 0 || end < begin || end >= codePointCount) continue;
                    var beginOffset = offsets[begin];
                    var endOffset = offsets[end + 1];
                    if (!boundary.isBoundary(beginOffset) || !boundary.isBoundary(endOffset)) continue;
                    result.add(new Emote(id, beginOffset, endOffset));
                } catch (NumberFormatException invalidRange) {
                    // 不正な位置情報だけを除外し、本文と他の範囲を保つ。
                    continue;
                }
            }
        }
        result.sort(Comparator.comparingInt(Emote::begin));
        var nonOverlapping = new ArrayList<Emote>();
        var previousEnd = 0;
        for (var emote : result) {
            if (emote.begin() < previousEnd) continue;
            nonOverlapping.add(emote);
            previousEnd = emote.endExclusive();
        }
        return List.copyOf(nonOverlapping);
    }

    private int[] codePointOffsets(String message) {
        var count = message.codePointCount(0, message.length());
        var offsets = new int[count + 1];
        for (var index = 0; index < count; index++) {
            offsets[index + 1] = offsets[index] + Character.charCount(message.codePointAt(offsets[index]));
        }
        return offsets;
    }
}
