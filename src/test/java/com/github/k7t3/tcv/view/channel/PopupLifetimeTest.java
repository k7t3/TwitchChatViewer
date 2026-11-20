package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.view.core.BasicPopup;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PopupLifetimeTest {
    @Test void aDisposedPopupSkinDoesNotAttachLaterContentToItsOldLayout(FxRobot robot) {
        robot.interact(() -> {
            var window = new Stage(); window.setScene(new Scene(new StackPane(), 400, 300)); window.show();
            var popup = new BasicPopup(new Label("first"));
            try {
                popup.show(window, 20, 20);
                popup.getSkin().dispose();
                var next = new Label("next"); popup.setContent(next);
                assertThat(next.getParent()).isNull();
            } finally { popup.hide(); window.close(); }
        });
    }

    @Test
    void reopeningLiveInfoShowsTheLatestStreamDetails(FxRobot robot) {
        robot.interact(() -> {
            var source = mock(TwitchChannel.class);
            when(source.getBroadcaster()).thenReturn(new Broadcaster("1", "channel", "Channel", "", null));
            var channel = new TwitchChannelViewModel(source, mock(ChannelViewModelRepository.class),
                    null, Runnable::run, ignored -> {});
            var startedAt = LocalDateTime.now().minusMinutes(10);
            channel.updateStreamInfo(new StreamInfo("1", "10", "First game", "First title", List.of(),
                    12, startedAt, Duration.ofMinutes(10), "ja"));
            var window = new Stage();
            window.setScene(new Scene(new StackPane(), 400, 300));
            window.show();
            try (channel; var popup = new LiveInfoPopup(channel)) {
                popup.show(window, 220, 200);
                assertThat(popup.getContent().lookupAll(".label"))
                        .extracting(node -> ((Label) node).getText())
                        .contains("First game", "First title", "12");
                popup.hide();
                channel.updateStreamInfo(new StreamInfo("1", "20", "Second game", "Second title", List.of(),
                        34, startedAt, Duration.ofMinutes(10), "ja"));

                popup.show(window, 220, 200);

                assertThat(popup.getContent().lookupAll(".label"))
                        .extracting(node -> ((Label) node).getText())
                        .contains("Second game", "Second title", "34")
                        .doesNotContain("First game", "First title", "12");
            } finally {
                window.close();
            }
        });
    }

    @Test void closingLiveInfoReleasesItsChannelAndShowingHandler(FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(TwitchChannelViewModel.class, RETURNS_DEEP_STUBS);
            when(channel.getStreamInfo().startedAt()).thenReturn(LocalDateTime.now());
            var window = new Stage(); window.setScene(new Scene(new StackPane(), 400, 300)); window.show();
            var popup = new LiveInfoPopup(channel); var content = (LiveInfoView) popup.getContent();
            try {
                popup.show(window, 20, 20); popup.close(); popup.close();
                assertThat(popup.isShowing()).isFalse(); assertThat(popup.getContent()).isNull();
                assertThat(content.getChannel()).isNull();
                assertThatCode(() -> popup.fireEvent(new WindowEvent(popup, WindowEvent.WINDOW_SHOWING))).doesNotThrowAnyException();
            } finally { popup.close(); window.close(); }
        });
    }
}
