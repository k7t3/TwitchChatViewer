package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class TwitchChannelImageTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void channelImagesUseTheSharedImageService(boolean offline, FxRobot robot) {
        robot.interact(() -> {
            var profileUrl = "file:/missing-profile.png";
            var offlineUrl = "file:/missing-offline.png";
            var value = new ReadOnlyObjectWrapper<Image>();
            var image = mock(LazyImage.class);
            when(image.valueProperty()).thenReturn(value.getReadOnlyProperty());
            var images = mock(ImageLoader.class);
            when(images.load(offline ? offlineUrl : profileUrl, offline ? 0 : 64, offline ? 0 : 64)).thenReturn(image);
            var channel = mock(TwitchChannel.class);
            when(channel.getBroadcaster()).thenReturn(new Broadcaster("123", "channel", "Channel", profileUrl, offlineUrl));
            var model = new TwitchChannelViewModel(channel, mock(ChannelViewModelRepository.class), images, Runnable::run, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });

            if (offline) {
                assertThat(model.getOfflineImage()).isNull();
                var decoded = new WritableImage(2, 2);
                value.set(decoded);
                assertThat(model.getOfflineImage()).isSameAs(decoded);
            } else {
                assertThat(model.getProfileImage()).isSameAs(image);
            }
        });
    }
}
