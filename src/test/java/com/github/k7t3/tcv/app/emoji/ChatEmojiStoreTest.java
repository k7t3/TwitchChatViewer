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

package com.github.k7t3.tcv.app.emoji;

import com.github.k7t3.tcv.LoggerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ChatEmojiStoreTest {

    private static Logger log;

    private Emoji emoji;
    private ChatEmojiStore emojiCache;

    @BeforeAll
    static void init() {
        log = LoggerProvider.getLogger(ChatEmojiStoreTest.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        var dir = Files.createTempDirectory("tcv");
        emoji = new Emoji(dir);
        emoji.extractArchive();
        emojiCache = new ChatEmojiStore(emoji);
    }

    @AfterEach
    void tearDown() throws IOException {
        emoji.close();
        log.debug("delete {}", emoji.getArchivePath());
        Files.deleteIfExists(emoji.getArchivePath());

        log.debug("delete {}", emoji.getArchivePath().getParent());
        Files.delete(emoji.getArchivePath().getParent());
    }

    @Test
    void loadImage() throws IOException {
        assertTrue(emoji.validateArchive());

        var hex = "1f3f4-e0067-e0062-e0065-e006e-e0067-e007f";
        assertTrue(emoji.contains(hex));
        assertNotNull(emojiCache.get(hex));

        hex = "1f3f4-e0067-e0062-e0065-e006e-e0067-e007f";
        assertTrue(emoji.contains(hex));
        assertNotNull(emojiCache.get(hex));

        hex = "26a0";
        assertTrue(emoji.contains(hex));
        assertNotNull(emojiCache.get(hex));

        hex = "1f642";
        assertTrue(emoji.contains(hex));
        assertNotNull(emojiCache.get(hex));

        // ないやつ
        hex = "26a0-fe0f";
        assertFalse(emoji.contains(hex));
        assertNull(emojiCache.get(hex));
    }
}