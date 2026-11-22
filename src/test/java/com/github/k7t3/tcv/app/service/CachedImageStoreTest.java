package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.image.LazyImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javafx.scene.image.Image;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CachedImageStoreTest {
    @Test
    void aLoadThatClosesItsStoreReleasesTheReturnedImageInsteadOfPublishingIt() {
        var owner = new java.util.concurrent.atomic.AtomicReference<CachedImageStore<String>>();
        var image = mock(LazyImage.class);
        when(image.completion()).thenReturn(new CompletableFuture<>());
        var store = new CachedImageStore<String>(10, Duration.ofMinutes(5)) {
            @Override protected LazyImage loadImage(String key) {
                owner.get().close();
                return image;
            }
        };
        owner.set(store);
        try (store) {
            assertThatThrownBy(() -> store.get("badge"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("image store is closed");

            verify(image).close();
            store.close();
            verify(image, times(1)).close();
        }
    }

    @Test
    void invalidatingOneKeyCancelsAllItsPendingVersionsAndKeepsOtherKeys() {
        var first = mock(LazyImage.class); var next = mock(LazyImage.class); var other = mock(LazyImage.class);
        for (var image : java.util.List.of(first, next, other)) when(image.completion()).thenReturn(new CompletableFuture<>());
        var count = new AtomicInteger();
        var store = new CachedImageStore<String>(10, Duration.ZERO) {
            @Override protected LazyImage loadImage(String key) {
                return key.equals("other") ? other : count.incrementAndGet() == 1 ? first : next;
            }
        };
        try {
            store.get("clip"); store.get("clip"); store.get("other");
            store.invalidate("clip");
            verify(first).close(); verify(next).close(); verify(other, never()).close();
            store.close();
            verify(first, times(1)).close(); verify(next, times(1)).close(); verify(other).close();
        } finally { store.close(); }
    }

    @Test
    void invalidationAllowsAnExplicitReloadWithoutChangingTheExpiryPolicy() {
        var first = mock(LazyImage.class); var next = mock(LazyImage.class);
        when(first.completion()).thenReturn(CompletableFuture.completedFuture(mock(Image.class)));
        when(next.completion()).thenReturn(new CompletableFuture<>());
        var count = new AtomicInteger();
        var store = new CachedImageStore<String>(10, Duration.ofMinutes(5)) {
            @Override protected LazyImage loadImage(String key) { return count.incrementAndGet() == 1 ? first : next; }
        };
        try {
            assertThat(store.get("clip")).isSameAs(first);
            store.invalidate("clip");
            assertThat(store.get("clip")).isSameAs(next);
            verify(first).close();
        } finally { store.close(); }
    }


    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closedStoreRejectsFurtherLoading(boolean optional) {
        var loads = new AtomicInteger();
        var store = new CachedImageStore<String>(10, Duration.ofMinutes(5)) {
            @Override
            protected LazyImage loadImage(String key) {
                loads.incrementAndGet();
                return null;
            }
        };
        store.close();

        assertThatThrownBy(() -> {
            if (optional) store.getNullable("badge");
            else store.get("badge");
        }).isInstanceOf(IllegalStateException.class).hasMessage("image store is closed");
        assertThat(loads.get()).isZero();
    }

    @Test
    void completedImagesAreNotRetainedAfterExpiry() {
        var completion = new CompletableFuture<Image>();
        var image = mock(LazyImage.class);
        when(image.completion()).thenReturn(completion);
        var store = new CachedImageStore<String>(10, Duration.ZERO) {
            @Override
            protected LazyImage loadImage(String key) {
                return image;
            }
        };
        try {
            assertThat(store.get("badge")).isSameAs(image);
            completion.complete(mock(Image.class));

            store.close();

            verify(image, never()).close();
        } finally {
            store.close();
        }
    }

    @Test
    void expiredImagesContinueLoadingUntilTheirOwningStoreCloses() {
        var first = mock(LazyImage.class);
        var second = mock(LazyImage.class);
        when(first.completion()).thenReturn(new CompletableFuture<>());
        when(second.completion()).thenReturn(new CompletableFuture<>());
        var loads = new AtomicInteger();
        var store = new CachedImageStore<String>(10, Duration.ZERO) {
            @Override
            protected LazyImage loadImage(String key) {
                return loads.incrementAndGet() == 1 ? first : second;
            }
        };
        try {
            assertThat(store.get("badge")).isSameAs(first);
            assertThat(store.get("badge")).isSameAs(second);
            verify(first, never()).close();
            verify(second, never()).close();

            store.close();

            verify(first).close();
            verify(second).close();
        } finally {
            store.close();
        }
    }

    @Test
    void closingStoreReleasesEachCachedImageOnce() {
        var image = mock(LazyImage.class);
        when(image.completion()).thenReturn(new CompletableFuture<>());
        var store = new CachedImageStore<String>(10, Duration.ofMinutes(5)) {
            @Override
            protected LazyImage loadImage(String key) {
                return image;
            }
        };
        assertThat(store.get("badge")).isSameAs(image);
        assertThat(store.get("badge")).isSameAs(image);

        store.close();
        store.close();

        verify(image, times(1)).close();
    }
}
