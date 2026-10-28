package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatBadge;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelImageLifecycleTest {

    @ParameterizedTest(name = "late image after close, offline={0}")
    @ValueSource(booleans = {false, true})
    void imagesReturnedAfterReentrantChannelCloseAreReleased(boolean offline, FxRobot robot) {
        robot.interact(() -> {
            var fixture = new Fixture();
            var image = offline ? fixture.offline : fixture.profile;
            var url = offline ? "offline" : "profile";
            var size = offline ? 0 : 64;
            when(fixture.images.load(url, size, size)).thenAnswer(invocation -> {
                fixture.channel.close();
                return image;
            });
            try {
                assertThatThrownBy(() -> {
                    if (offline) fixture.channel.getOfflineImage();
                    else fixture.channel.getProfileImage();
                }).isInstanceOf(IllegalStateException.class).hasMessage("channel view model is closed");

                verify(image).close();
                if (offline) verify(image, never()).valueProperty();
                fixture.channel.close();
                verify(image, times(1)).close();
                verify(fixture.images, never()).close();
            } finally {
                fixture.repository.close();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingTheSessionReleasesChannelImagesAndBindings(boolean logout, FxRobot robot) {
        robot.interact(() -> {
            var fixture = new Fixture();
            try {
                fixture.requestImages();

                if (logout) fixture.repository.onLogout();
                else fixture.repository.close();
                fixture.offlineValue.set(new WritableImage(3, 3));

                verify(fixture.profile).close();
                verify(fixture.offline).close();
                verify(fixture.badgeImage).close();
                verify(fixture.images, never()).close();
                assertThat(fixture.offlineProperty.get()).isNull();
                assertThat(fixture.repository.getChannels()).isEmpty();
                assertThat(fixture.repository.getRepository()).isNull();
                assertThatThrownBy(fixture.channel::getProfileImage).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(fixture.channel::getOfflineImage).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> fixture.channel.getChatBadgeStore().get(fixture.badge))
                        .isInstanceOf(IllegalStateException.class);
            } finally {
                fixture.repository.close();
            }
        });
    }

    @Test
    void oneImageFailureDoesNotPreventOtherImagesFromBeingReleased(FxRobot robot) {
        robot.interact(() -> {
            var fixture = new Fixture();
            fixture.requestImages();
            var failure = new IllegalStateException("profile close failed");
            doThrow(failure).when(fixture.profile).close();
            try {
                assertThatThrownBy(fixture.repository::onLogout).isSameAs(failure);

                verify(fixture.offline).close();
                verify(fixture.badgeImage).close();
                // ドメインの解放は ClientSession。画面解放の失敗後の継続は AppHelperSessionTest で検証する。
                verify(fixture.domain, never()).clear();
                assertThat(fixture.offlineProperty.get()).isNull();
                assertThat(fixture.repository.getChannels()).isEmpty();
                assertThat(fixture.repository.getRepository()).isNull();
                fixture.repository.close();
                verify(fixture.profile).close();
            } finally {
                fixture.repository.close();
            }
        });
    }

    private static class Fixture {
        final ImageLoader images = mock(ImageLoader.class);
        final ChannelRepository domain = mock(ChannelRepository.class);
        final ChannelViewModelRepository repository = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), images, Runnable::run, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        final LazyImage profile = image();
        final LazyImage offline = image();
        final LazyImage badgeImage = image();
        final ReadOnlyObjectWrapper<Image> offlineValue = new ReadOnlyObjectWrapper<>();
        final ChatBadge badge = new ChatBadge("moderator", "1");
        final TwitchChannelViewModel channel;
        javafx.beans.property.ReadOnlyObjectProperty<Image> offlineProperty;

        Fixture() {
            var model = mock(TwitchChannel.class);
            when(model.getBroadcaster()).thenReturn(new Broadcaster("123", "channel", "Channel", "profile", "offline"));
            when(model.getBadgeUrl(badge)).thenReturn(Optional.of("badge"));
            when(images.load("profile", 64, 64)).thenReturn(profile);
            when(images.load("offline", 0, 0)).thenReturn(offline);
            when(images.load("badge", 24, 24)).thenReturn(badgeImage);
            when(offline.valueProperty()).thenReturn(offlineValue.getReadOnlyProperty());
            repository.setRepository(domain);
            channel = new TwitchChannelViewModel(model, repository, images, Runnable::run, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
            repository.getChannels().add(channel);
        }

        void requestImages() {
            assertThat(channel.getProfileImage()).isSameAs(profile);
            offlineProperty = channel.offlineImageProperty();
            assertThat(channel.getChatBadgeStore().get(badge)).isSameAs(badgeImage);
        }

        private static LazyImage image() {
            var image = mock(LazyImage.class);
            when(image.completion()).thenReturn(new CompletableFuture<>());
            return image;
        }
    }
}
