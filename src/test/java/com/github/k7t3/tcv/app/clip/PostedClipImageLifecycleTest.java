package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostedClipImageLifecycleTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingTheSessionReleasesItsImagesWithoutClosingTheSharedService(boolean logout) {
        var profileUrl = "https://images.example/profile.png";
        var thumbnailUrl = "https://images.example/thumbnail.png";
        var profile = mock(LazyImage.class);
        var thumbnail = mock(LazyImage.class);
        when(profile.completion()).thenReturn(new CompletableFuture<>());
        when(thumbnail.completion()).thenReturn(new CompletableFuture<>());
        var images = mock(ImageLoader.class);
        when(images.load(profileUrl, 30, 30)).thenReturn(profile);
        when(images.load(thumbnailUrl, 128, 128)).thenReturn(thumbnail);
        var repository = new PostedClipRepository(images, Runnable::run, ignored -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var clip = mock(VideoClip.class);
        when(clip.thumbnailUrl()).thenReturn(thumbnailUrl);
        var item = new PostedClipItem(repository, "clip", "https://clips.twitch.tv/clip", clip);
        var owner = new Broadcaster("123", "channel", "Channel", profileUrl, "");
        try {
            assertThat(item.getThumbnailImage()).isSameAs(thumbnail);
            assertThat(repository.getBroadcasterImage(owner)).isSameAs(profile);

            if (logout) repository.onLogout();
            else repository.close();

            verify(profile).close();
            verify(thumbnail).close();
            verify(images, never()).close();
        } finally {
            repository.close();
        }
    }
}
