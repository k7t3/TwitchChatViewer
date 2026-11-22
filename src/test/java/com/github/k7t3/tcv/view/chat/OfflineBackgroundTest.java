package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class OfflineBackgroundTest {
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void displaysAnImageThatArrivesAfterGoingOffline(FxRobot robot) {
        robot.interact(() -> {
            var live = new SimpleBooleanProperty(true);
            var image = new ReadOnlyObjectWrapper<Image>();
            var channel = mock(TwitchChannelViewModel.class);
            when(channel.liveProperty()).thenReturn(live);
            when(channel.isLive()).thenAnswer(invocation -> live.get());
            when(channel.offlineImageProperty()).thenReturn(image.getReadOnlyProperty());
            var background = ChatRoomViewUtils.offlineBackground(channel);
            var pane = new Pane();
            pane.backgroundProperty().bind(background);
            try {
                stage.setScene(new Scene(pane, 320, 180));
                stage.show();
                assertThat(pane.getBackground()).isNull();
                verify(channel, never()).offlineImageProperty();
                live.set(false);
                assertThat(pane.getBackground()).isNull();

                var decoded = new WritableImage(16, 16);
                for (var y = 0; y < 16; y++) {
                    for (var x = 0; x < 16; x++) decoded.getPixelWriter().setColor(x, y, Color.RED);
                }
                image.set(decoded);

                assertThat(pane.getBackground().getImages()).hasSize(1);
                assertThat(pane.getBackground().getImages().getFirst().getImage()).isSameAs(decoded);
                pane.applyCss();
                pane.layout();
                assertThat(pane.getOpacity()).isEqualTo(1);
                assertThat(pane.snapshot(null, null).getPixelReader().getColor(20, 20)).isEqualTo(Color.RED);
                live.set(true);
                assertThat(pane.getBackground()).isNull();
                live.set(false);
                assertThat(pane.getBackground().getImages().getFirst().getImage()).isSameAs(decoded);
                image.set(null);
                assertThat(pane.getBackground()).isNull();
            } finally {
                pane.backgroundProperty().unbind();
                background.dispose();
                stage.hide();
            }
        });
    }
}
