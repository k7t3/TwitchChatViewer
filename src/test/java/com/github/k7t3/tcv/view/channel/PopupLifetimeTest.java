package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.view.core.BasicPopup;
import java.time.LocalDateTime;
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
