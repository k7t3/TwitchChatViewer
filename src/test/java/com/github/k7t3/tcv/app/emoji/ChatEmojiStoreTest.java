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

import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class ChatEmojiStoreTest {

    @TempDir
    Path directory;

    private Emoji emoji;
    private ChatEmojiStore emojiCache;
    private ImageLoader images;
    private ExecutorService worker;

    @BeforeEach
    void setUp() throws IOException {
        emoji = new Emoji(directory);
        emoji.extractArchive();
        assertThat(emoji.validateArchive()).isTrue();
        worker = Executors.newVirtualThreadPerTaskExecutor();
        images = new ImageLoader(worker);
        emojiCache = new ChatEmojiStore(emoji, images);
    }

    @AfterEach
    void tearDown() {
        CloseActions.runAll(() -> { if (emojiCache != null) emojiCache.close(); },
                () -> { if (images != null) images.close(); },
                () -> { if (emoji != null) emoji.close(); },
                () -> { if (worker != null) worker.close(); });
    }

    @Test
    void explicitInvalidationRetriesAFailedArchiveRead(FxRobot robot) throws Exception {
        var failure = new IOException("archive image read failed");
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        try (var source = new Emoji(directory) {
            @Override public java.io.InputStream openImageStream(String key) throws IOException {
                if (attempts.incrementAndGet() == 1) throw failure;
                return super.openImageStream(key);
            }
        }; var store = new ChatEmojiStore(source, images)) {
            assertThat(source.validateArchive()).isTrue();
            var first = new AtomicReference<LazyImage>();
            robot.interact(() -> first.set(store.get("1f642")));
            assertThatThrownBy(() -> first.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .hasCause(failure);

            var retried = new AtomicReference<LazyImage>();
            robot.interact(() -> {
                assertThat(store.get("1f642")).isSameAs(first.get());
                store.invalidate("1f642");
                retried.set(store.get("1f642"));
                assertThat(retried.get()).isNotSameAs(first.get());
            });
            var decoded = retried.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS);

            robot.interact(() -> {
                assertThat(decoded.isError()).isFalse();
                assertThat(decoded.getWidth()).isEqualTo(32);
                assertThat(decoded.getHeight()).isEqualTo(32);
                assertThat(store.get("1f642")).isSameAs(retried.get());
            });
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void loadsAndReusesDecodedArchiveImages(FxRobot robot) throws Exception {
        var keys = List.of("1f3f4-e0067-e0062-e0065-e006e-e0067-e007f", "26a0", "1f642");
        var images = new AtomicReference<List<LazyImage>>();
        robot.interact(() -> images.set(keys.stream().map(emojiCache::get).toList()));

        for (var image : images.get()) {
            image.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }

        robot.interact(() -> {
            for (var image : images.get()) {
                assertThat(image.getValue().isError()).isFalse();
                assertThat(image.getValue().getWidth()).isEqualTo(32);
                assertThat(image.getValue().getHeight()).isEqualTo(32);
            }
            assertThat(emojiCache.get(keys.getFirst())).isSameAs(images.get().getFirst());
            assertThat(emojiCache.getNullable("26a0-fe0f")).isEmpty();
        });
    }
}
