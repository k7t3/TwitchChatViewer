package com.github.k7t3.tcv.chat;

import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.domain.chat.ChatMessageParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageParserTest {

    private ChatMessageParser parser;

    @BeforeEach
    void setUp() {
        parser = new ChatMessageParser();
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