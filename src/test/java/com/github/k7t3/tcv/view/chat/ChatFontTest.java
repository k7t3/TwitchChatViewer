package com.github.k7t3.tcv.view.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatFontTest {

    @Test
    void defaultFontTest() {
        var chatFont = ChatFont.getDefault();
        assertEquals("System", chatFont.getFamily());

        var chatFont2 = new ChatFont(chatFont.getFamily(), chatFont.getSize());
        assertEquals(chatFont, chatFont2);
    }
}