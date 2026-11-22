package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.image.ImageLoader;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatResourcesLoaderTest {

    @Test
    void aFailedCleanupIsDistinguishedFromAnOrdinaryLoadFailure() throws Exception {
        var emoji = mock(Emoji.class);
        when(emoji.validateArchive()).thenReturn(true);
        var loadFailure = new IllegalStateException("badges could not be loaded");
        var cleanupFailure = new IllegalStateException("archive could not be closed");
        doThrow(cleanupFailure).when(emoji).close();
        var loader = new ChatResourcesLoader(() -> emoji, () -> { throw loadFailure; },
                () -> new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));

        assertThatThrownBy(loader::load).hasCause(loadFailure).satisfies(failure ->
                assertThat(failure.getCause().getSuppressed()).containsExactly(cleanupFailure));

        verify(emoji).close();
    }

    @Test
    void archiveInstallationCreatesTheMissingApplicationDirectory(@TempDir Path directory) throws Exception {
        var target = directory.resolve("new/application");
        var emoji = new Emoji(target);
        var loader = new ChatResourcesLoader(() -> emoji, () -> mock(GlobalChatBadges.class),
                () -> new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));

        try (var resources = loader.load()) {
            assertThat(Files.isRegularFile(emoji.getArchivePath())).isTrue();
            assertThat(resources.emoji().contains("1f642")).isTrue();
        }
    }

    @Test
    void bundledArchiveIsUsableUntilTheLoadedResourcesAreClosed(@TempDir Path directory) throws Exception {
        var emoji = new Emoji(directory);
        var badges = mock(GlobalChatBadges.class);
        var filters = new ChatFilters.Snapshot(List.of(), List.of());
        var loader = new ChatResourcesLoader(() -> emoji, () -> badges, () -> filters, mock(ImageLoader.class));
        try (var resources = loader.load()) {
            assertThat(resources.badges()).isSameAs(badges);
            assertThat(resources.filters()).isEqualTo(filters);
            assertThat(resources.emoji().contains("1f642")).isTrue();
            try (var input = resources.emoji().openImageStream("1f642")) {
                var image = ImageIO.read(input);
                assertThat(image).isNotNull();
                assertThat(image.getWidth()).isEqualTo(72);
                assertThat(image.getHeight()).isEqualTo(72);
            }
        }

        assertThatThrownBy(() -> emoji.contains("1f642"))
                .isInstanceOf(IllegalStateException.class).hasMessage("closed");
    }

    @Test
    void invalidExtractedArchiveFailsAndIsReleased() throws Exception {
        var emoji = mock(Emoji.class);
        when(emoji.validateArchive()).thenReturn(false);
        var loader = new ChatResourcesLoader(() -> emoji, () -> mock(GlobalChatBadges.class),
                () -> new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));

        assertThatThrownBy(loader::load).isInstanceOf(IOException.class)
                .hasMessage("emoji archive validation failed");

        verify(emoji).extractArchive();
        verify(emoji).close();
    }

    @Test
    void failedBadgeLoadReleasesThePreviouslyOpenedEmojiArchive() throws Exception {
        var emoji = mock(Emoji.class);
        when(emoji.validateArchive()).thenReturn(true);
        var failure = new IllegalStateException("global badges could not be loaded");
        var loader = new ChatResourcesLoader(() -> emoji, () -> { throw failure; },
                () -> new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));

        assertThatThrownBy(loader::load).isSameAs(failure);

        verify(emoji).close();
    }
}
