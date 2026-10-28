package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class FoundChannelImageTest {

    @Test
    void replacingTheProfileReleasesThePreviousImage(FxRobot robot) {
        robot.interact(() -> {
            var oldValue = new ReadOnlyObjectWrapper<Image>();
            var newValue = new ReadOnlyObjectWrapper<Image>();
            var first = mock(LazyImage.class);
            var second = mock(LazyImage.class);
            when(first.valueProperty()).thenReturn(oldValue.getReadOnlyProperty());
            when(second.valueProperty()).thenReturn(newValue.getReadOnlyProperty());
            var images = mock(ImageLoader.class);
            var firstUrl = "https://images.example/first.png";
            var secondUrl = "https://images.example/second.png";
            when(images.load(firstUrl, 32, 32)).thenReturn(first);
            when(images.load(secondUrl, 32, 32)).thenReturn(second);
            var model = new FoundChannelViewModel(mock(ChannelViewModelRepository.class),
                    new FoundChannel(new Broadcaster("123", "first", "First", firstUrl, ""), true, "First game"), images, failure -> { throw new AssertionError(failure); }, mock(BrowserActions.class));
            try {
                model.update(new FoundChannel(new Broadcaster("456", "second", "Second", secondUrl, ""), false, "Second game"));

                verify(first).close();
                oldValue.set(new WritableImage(3, 3));
                assertThat(model.getProfileImage()).isNull();
                var decoded = new WritableImage(2, 2);
                newValue.set(decoded);
                assertThat(model.getProfileImage()).isSameAs(decoded);
                assertThat(model.getBroadcaster().getUserId()).isEqualTo("456");
                model.close();
                verify(second).close();
            } finally {
                model.close();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void profileImageIsBoundOnlyForTheResultModelsLifetime(boolean logout, FxRobot robot) {
        robot.interact(() -> {
            var url = "https://images.example/profile.png";
            var value = new ReadOnlyObjectWrapper<Image>();
            var image = mock(LazyImage.class);
            when(image.valueProperty()).thenReturn(value.getReadOnlyProperty());
            var images = mock(ImageLoader.class);
            when(images.load(url, 32, 32)).thenReturn(image);
            var channel = new FoundChannel(new Broadcaster("123", "channel", "Channel", url, ""), true, "Game");
            var model = new FoundChannelViewModel(mock(ChannelViewModelRepository.class), channel, images, failure -> { throw new AssertionError(failure); }, mock(BrowserActions.class));
            try {
                assertThat(model.getProfileImage()).isNull();
                var decoded = new WritableImage(2, 2);
                value.set(decoded);
                assertThat(model.getProfileImage()).isSameAs(decoded);

                if (logout) model.onLogout();
                else model.close();
                value.set(new WritableImage(3, 3));

                assertThat(model.getProfileImage()).isNull();
                verify(image).close();
                verify(images, never()).close();
            } finally {
                model.close();
            }
        });
    }
}
