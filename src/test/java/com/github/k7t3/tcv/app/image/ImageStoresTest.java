package com.github.k7t3.tcv.app.image;

import com.github.k7t3.tcv.app.chat.ChannelChatBadgeStore;
import com.github.k7t3.tcv.app.chat.ChatEmoteStore;
import com.github.k7t3.tcv.app.chat.GlobalChatBadgeStore;
import com.github.k7t3.tcv.app.clip.ClipThumbnailStore;
import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.service.CachedImageStore;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageStoresTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("stores")
    void sharesTheImageServiceWhileOwningOnlyItsRequestedImages(StoreRequest<?> request) {
        assertRequest(request);
    }

    private static <K> void assertRequest(StoreRequest<K> request) {
        var image = mock(LazyImage.class);
        when(image.completion()).thenReturn(new CompletableFuture<>());
        var images = mock(ImageLoader.class);
        when(images.load(request.url(), request.width(), request.height())).thenReturn(image);
        try (var store = request.factory().apply(images)) {
            assertThat(store.get(request.key())).isSameAs(image);

            store.close();

            verify(image).close();
            verify(images, never()).close();
        }
    }

    private static Stream<StoreRequest<?>> stores() {
        var badge = new ChatBadge("moderator", "1");
        var globalUrl = "https://images.example/global.png";
        var channelUrl = "https://images.example/channel.png";
        var thumbnailUrl = "https://images.example/clip.png";
        var globals = mock(GlobalChatBadges.class);
        when(globals.getBadgeUrl("moderator", "1")).thenReturn(Optional.of(globalUrl));
        var channel = mock(TwitchChannel.class);
        when(channel.getBadgeUrl(badge)).thenReturn(Optional.of(channelUrl));
        var clip = mock(VideoClip.class);
        when(clip.thumbnailUrl()).thenReturn(thumbnailUrl);
        var item = new PostedClipItem(mock(PostedClipRepository.class), "clip", "https://clips.twitch.tv/clip", clip);
        return Stream.of(
                new StoreRequest<>("global badge", images -> new GlobalChatBadgeStore(globals, images), badge, globalUrl, 0, 0),
                new StoreRequest<>("channel badge", images -> new ChannelChatBadgeStore(channel, images), badge, channelUrl, 24, 24),
                new StoreRequest<>("emote", ChatEmoteStore::new, "25",
                        "https://static-cdn.jtvnw.net/emoticons/v2/25/default/light/1.0", 0, 0),
                new StoreRequest<>("clip thumbnail", ClipThumbnailStore::new, item, thumbnailUrl, 128, 128)
        );
    }

    private record StoreRequest<K>(String name, Function<ImageLoader, CachedImageStore<K>> factory,
                                   K key, String url, double width, double height) {
        @Override
        public String toString() {
            return name;
        }
    }
}
