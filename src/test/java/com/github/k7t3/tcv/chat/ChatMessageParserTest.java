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

package com.github.k7t3.tcv.chat;

import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.domain.chat.ChatMessageParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageParserTest {

    private ChatMessageParser parser;

    @BeforeEach
    void setUp() {
        parser = new ChatMessageParser();
    }

    @Test
    void aSingleCodePointEmoteKeepsTheFollowingText() {
        var message = parser.parse("A tail", "single:0-0");

        assertThat(message).containsExactly(
                ChatMessageFragment.emote("single", "A"),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo("A tail");
    }

    @Test
    void aMalformedEmoteEntryCannotDiscardTextOrAnotherValidEmote() {
        var message = parser.parse("hello Kappa tail", "broken/25:6-10");

        assertThat(message).containsExactly(
                ChatMessageFragment.text("hello "),
                ChatMessageFragment.emote("25", "Kappa"),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo("hello Kappa tail");
    }

    @ParameterizedTest(name = "malformed range [{index}]: {0}")
    @ValueSource(strings = {"", "bad", "6", "6-", "x-4", "6-x", "6-10-extra", "2147483648-2147483649"})
    void aMalformedRangeKeepsTextAndOtherRanges(String range) {
        var message = parser.parse("hello Kappa tail", "25:" + range + ",6-10");

        assertThat(message).containsExactly(
                ChatMessageFragment.text("hello "),
                ChatMessageFragment.emote("25", "Kappa"),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo("hello Kappa tail");
    }

    @ParameterizedTest(name = "outside message range [{index}]: {0}")
    @ValueSource(strings = {"-1-2", "2-1", "6-16", "6-999", "2147483647-2147483647"})
    void anInvalidPositionRangeCannotHideALaterValidEmote(String range) {
        var message = parser.parse("hello Kappa tail", "invalid:" + range + "/25:6-10");

        assertThat(message).containsExactly(
                ChatMessageFragment.text("hello "),
                ChatMessageFragment.emote("25", "Kappa"),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo("hello Kappa tail");
    }

    @ParameterizedTest(name = "overlapping ranges [{index}]: {0}")
    @ValueSource(strings = {"25:6-10,6-10", "25:6-10/other:8-12", "25:6-10/other:6-8", "25:6-10/other:6-12"})
    void duplicateOrOverlappingRangesCannotConsumeFollowingText(String emotes) {
        var message = parser.parse("hello Kappa tail", emotes);

        assertThat(message).containsExactly(
                ChatMessageFragment.text("hello "),
                ChatMessageFragment.emote("25", "Kappa"),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo("hello Kappa tail");
    }

    @ParameterizedTest(name = "blank emote id [{index}]: {0}")
    @ValueSource(strings = {"", " ", "\t"})
    void aBlankEmoteIdCannotReplaceAValidEmote(String id) {
        var message = parser.parse("hello Kappa tail", id + ":6-10/25:6-10");

        assertThat(message).containsExactly(
                ChatMessageFragment.text("hello "),
                ChatMessageFragment.emote("25", "Kappa"),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo("hello Kappa tail");
    }

    @ParameterizedTest(name = "whole grapheme emote [{index}]: {0}")
    @CsvSource(value = {"e\u0301|0-1", "1\uFE0F\u20E3|0-2", "👩‍💻|0-2"}, delimiter = '|')
    void aMultiCodePointEmoteKeepsTheFollowingText(String text, String range) {
        var message = parser.parse(text + " tail", "unicode:" + range);

        assertThat(message).containsExactly(
                ChatMessageFragment.emote("unicode", text),
                ChatMessageFragment.text(" tail"));
        assertThat(message.getPlain()).isEqualTo(text + " tail");
    }

    @ParameterizedTest(name = "split grapheme range [{index}]: {1}")
    @MethodSource("splitGraphemeRanges")
    void aRangeSplittingAGraphemeCannotDiscardTheMessage(String plain, String emotes,
                                                        List<ChatMessageFragment> expected) {
        var message = parser.parse(plain, emotes);

        assertThat(message).containsExactlyElementsOf(expected);
        assertThat(message.getPlain()).isEqualTo(plain);
    }

    private static Stream<Arguments> splitGraphemeRanges() {
        var combining = List.of(ChatMessageFragment.text("e\u0301 "),
                ChatMessageFragment.emote("25", "Kappa"), ChatMessageFragment.text(" tail"));
        var joined = List.of(ChatMessageFragment.emoji("👩‍💻", "1f469-200d-1f4bb"),
                ChatMessageFragment.text(" "), ChatMessageFragment.emote("25", "Kappa"),
                ChatMessageFragment.text(" tail"));
        return Stream.of(
                Arguments.of("e\u0301 Kappa tail", "invalid:0-0/25:3-7", combining),
                Arguments.of("e\u0301 Kappa tail", "invalid:1-1/25:3-7", combining),
                Arguments.of("👩‍💻 Kappa tail", "invalid:0-1/25:4-8", joined),
                Arguments.of("👩‍💻 Kappa tail", "invalid:1-2/25:4-8", joined));
    }

    @ParameterizedTest(name = "valid range representation [{index}]: {0}")
    @MethodSource("validRangeRepresentations")
    void validRangesKeepTheirOrderedRepresentation(String plain, String emotes,
                                                   List<ChatMessageFragment> expected) {
        var message = parser.parse(plain, emotes);

        assertThat(message).containsExactlyElementsOf(expected);
        assertThat(message.getPlain()).isEqualTo(plain);
    }

    private static Stream<Arguments> validRangeRepresentations() {
        return Stream.of(
                Arguments.of("Kappa Keepo Kappa", "25:0-4,12-16/1902:6-10", List.of(
                        ChatMessageFragment.emote("25", "Kappa"), ChatMessageFragment.text(" "),
                        ChatMessageFragment.emote("1902", "Keepo"), ChatMessageFragment.text(" "),
                        ChatMessageFragment.emote("25", "Kappa"))),
                Arguments.of("AKappa", "single:0-0/25:1-5", List.of(
                        ChatMessageFragment.emote("single", "A"), ChatMessageFragment.emote("25", "Kappa"))),
                Arguments.of("Kappa Keepo", "1902:6-10/25:0-4", List.of(
                        ChatMessageFragment.emote("25", "Kappa"), ChatMessageFragment.text(" "),
                        ChatMessageFragment.emote("1902", "Keepo"))),
                Arguments.of("Kappa🙂", "25:0-4", List.of(
                        ChatMessageFragment.emote("25", "Kappa"), ChatMessageFragment.emoji("🙂", "1f642"))),
                Arguments.of("👩‍💻 Kappa", "25:4-8", List.of(
                        ChatMessageFragment.emoji("👩‍💻", "1f469-200d-1f4bb"), ChatMessageFragment.text(" "),
                        ChatMessageFragment.emote("25", "Kappa"))),
                Arguments.of(" Kappa ", "25:1-5", List.of(
                        ChatMessageFragment.text(" "), ChatMessageFragment.emote("25", "Kappa"),
                        ChatMessageFragment.text(" "))),
                Arguments.of("hello", null, List.of(ChatMessageFragment.text("hello"))));
    }

    @ParameterizedTest(name = "empty message [{index}]")
    @NullAndEmptySource
    void anEmptyMessageStillHasOneEmptyTextFragment(String plain) {
        var message = parser.parse(plain, "25:0-4");

        assertThat(message).containsExactly(ChatMessageFragment.text(""));
        assertThat(message.getPlain()).isEmpty();
    }

    @Test
    void parse() {
        var message = "akariOtaku akariOtaku がんばれー akariOtaku akariOtaku";
        var emotes = "emotesv2_951897be762b4abbac1912e021f2bb73:0-9,11-20,28-37,39-48";

        var messages = parser.parse(message, emotes);
        assertEquals(7, messages.size());

        var emoteCount = messages.stream().filter(m -> m.type() == ChatMessageFragment.Type.EMOTE).count();
        assertEquals(4, emoteCount);
    }

    @Test
    void testParseEmojiEmotes() {
        var plain = "\uD83D\uDE42 Kappa Hello guys";
        var emotes = "kappa_emote:2-6";
        var message = parser.parse(plain, emotes);

        var emoji = message.getFirst();
        assertEquals(ChatMessageFragment.Type.EMOJI, emoji.type());
        assertEquals("\uD83D\uDE42", emoji.text());

        var space = message.get(1);
        assertEquals(ChatMessageFragment.Type.TEXT, space.type());
        assertEquals(" ", space.text());

        var emote = message.get(2);
        assertEquals(ChatMessageFragment.Type.EMOTE, emote.type());
        assertEquals("kappa_emote", emote.text());
        assertEquals("Kappa", emote.additional());

        var text = message.get(3);
        assertEquals(ChatMessageFragment.Type.TEXT, text.type());
        assertEquals(" Hello guys", text.text());
    }

    @Test
    void testParseEmojiEmotes2() {
        var plain = "⚠️ Kappa Hello guys";
        var emotes = "kappa_emote:3-7";
        var message = parser.parse(plain, emotes);

        var emoji = message.getFirst();
        assertEquals(ChatMessageFragment.Type.EMOJI, emoji.type());
        assertEquals("⚠️", emoji.text());

        var space = message.get(1);
        assertEquals(ChatMessageFragment.Type.TEXT, space.type());
        assertEquals(" ", space.text());

        var emote = message.get(2);
        assertEquals(ChatMessageFragment.Type.EMOTE, emote.type());
        assertEquals("kappa_emote", emote.text());

        var text = message.get(3);
        assertEquals(ChatMessageFragment.Type.TEXT, text.type());
        assertEquals(" Hello guys", text.text());
    }

    @Test
    void testParseEmoji() {
        var england = "これイングランドの国旗です→\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F←これイングランドの国旗です";
        var message = parser.parse(england, null);

        var first = message.getFirst();
        assertEquals(ChatMessageFragment.Type.TEXT, first.type());
        assertEquals("これイングランドの国旗です→", first.text());

        var second = message.get(1);
        assertEquals(ChatMessageFragment.Type.EMOJI, second.type());
        assertEquals("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F", second.text());

        var third = message.get(2);
        assertEquals(ChatMessageFragment.Type.TEXT, third.type());
        assertEquals("←これイングランドの国旗です", third.text());
    }
}
