package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class LiveNotificationViewLifetimeTest {
    @Test
    void openingHistoryDelegatesToItsOwnerInsteadOfPublishingTheRetiredModel(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.button().fire();
                verify(f.model).openChannelAsync(f.record);
                verify(f.model, never()).publish(any(ChatOpeningEvent.class));
            }
        });
    }

    @Test
    void closingTheViewDetachesItsItemsCellsAndActionsWithoutClosingItsModel(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                var button = f.button(); assertThat(button.disableProperty().isBound()).isTrue();
                f.view.close();
                assertThat(button.disableProperty().isBound()).isFalse();
                assertThat(button.getOnAction()).isNull(); assertThat(f.view.getItems()).isEmpty();
                button.fire(); verify(f.model, never()).openChannelAsync(any()); verify(f.model, never()).close();
            }
        });
    }

    @Test
    void removingHistoryDetachesTheReusedCellsOldLiveBinding(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                var button = f.button();
                f.model.getRecords().clear(); f.view.layout();
                assertThat(button.disableProperty().isBound()).isFalse();
                button.fire(); verify(f.model, never()).openChannelAsync(any());
                f.live.set(false);
                f.model.getRecords().add(f.record); f.view.layout();
                assertThat(f.button().isDisabled()).isTrue();
                f.live.set(true); assertThat(f.button().isDisabled()).isFalse();
                f.button().fire(); verify(f.model).openChannelAsync(f.record);
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final LiveStateNotificator model = mock(LiveStateNotificator.class);
        final TwitchChannelViewModel retired = mock(TwitchChannelViewModel.class);
        final ReadOnlyBooleanWrapper live = new ReadOnlyBooleanWrapper(true);
        final LiveStateNotificator.LiveStateRecord record = new LiveStateNotificator.LiveStateRecord(retired, LocalTime.NOON, true);
        final LiveStateNotificatorView view;
        final Stage stage = new Stage();
        Fixture() {
            when(retired.isRetiring()).thenReturn(true);
            when(retired.getUserName()).thenReturn("Channel"); when(retired.liveProperty()).thenReturn(live.getReadOnlyProperty());
            when(model.canOpen(record)).thenReturn(true); when(model.openChannelAsync(record)).thenReturn(FXTask.of(null));
            when(model.getRecords()).thenReturn(FXCollections.observableArrayList(record));
            view = new LiveStateNotificatorView(model);
            var root = new StackPane(view); stage.setScene(new Scene(root, 400, 180)); stage.show(); root.applyCss(); root.layout();
        }
        Button button() { return view.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> "Open".equals(button.getText())).findFirst().orElseThrow(); }
        @Override public void close() { view.close(); stage.hide(); stage.setScene(null); }
    }
}
