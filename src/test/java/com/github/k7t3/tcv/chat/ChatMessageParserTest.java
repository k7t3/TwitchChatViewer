package com.github.k7t3.tcv.chat;

import com.github.k7t3.tcv.domain.chat.ChatMessage;
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
        var emotes = "emotes=emotesv2_951897be762b4abbac1912e021f2bb73:0-9,11-20,28-37,39-48";

        var messages = parser.parse(message, emotes);
        assertEquals(7, messages.size());

        var emoteCount = messages.stream().filter(m -> m.type() == ChatMessage.Type.EMOTE).count();
        assertEquals(4, emoteCount);
    }
}