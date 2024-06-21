package com.github.k7t3.tcv.app.emoji;

import com.github.k7t3.tcv.LoggerProvider;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class EmojiTest extends ApplicationTest {

    private Logger log;

    private Emoji emoji;

    @Override
    public void start(Stage stage) throws Exception {
        log = LoggerProvider.getLogger(this.getClass());
    }

    @BeforeEach
    void setUp() throws IOException {
        var dir = Files.createTempDirectory("tcv");
        emoji = new Emoji(dir);
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
    void contains() throws IOException {
        emoji.extractArchive();
        assertTrue(emoji.validateArchive());

        assertTrue(emoji.contains("1f3f4-e0067-e0062-e0065-e006e-e0067-e007f"));
        //assertTrue(emoji.contains("26a0-fe0f")); // ←これないです
        assertTrue(emoji.contains("26a0"));
        assertTrue(emoji.contains("1f642"));
        assertFalse(emoji.contains(null));
        assertFalse(emoji.contains("hello"));
    }

    @Test
    void openImageStream() throws IOException {
        emoji.extractArchive();
        assertTrue(emoji.validateArchive());

        var hex = "1f3f4-e0067-e0062-e0065-e006e-e0067-e007f";
        assertTrue(emoji.contains(hex));
        try (var input = emoji.openImageStream(hex)) {
            var image = new Image(input);
            assertTrue(0 < image.getWidth());
            assertTrue(0 < image.getHeight());
        }
    }

    @Test
    void extractArchiveAsync() throws IOException {
        assertFalse(emoji.validateArchive());

        emoji.extractArchive();

        assertTrue(emoji.validateArchive());
    }

    @Test
    void loadPropertiesAsync() throws IOException {
        assertFalse(emoji.validateArchive());

        var properties = emoji.getProperties();
        assertNotNull(properties);
    }
}