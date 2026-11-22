package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.WindowBounds;
import javafx.scene.layout.Pane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class FloatableBoundsOwnershipTest {
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void closingTheWindowStopsPendingAndAlreadyQueuedBounds(boolean shown, boolean completed, FxRobot robot) {
        var reference = new AtomicReference<FloatableStage>();
        var source = FXTask.task(() -> new WindowBounds(160, 140, 360, 420, false));
        var xAfterClose = new double[1];
        try {
            robot.interact(() -> {
                var service = mock(WindowBoundsService.class); when(service.getBoundsAsync("room")).thenReturn(source);
                var room = mock(ChatRoomViewModel.class); when(room.getIdentity()).thenReturn("room");
                var stage = new FloatableStage(new Pane()); reference.set(stage);
                if (shown) stage.show();
                stage.setX(80); stage.setY(90); stage.setWidth(420); stage.setHeight(600);
                ChatRoomViewUtils.initializeFloatableStage(stage, room, service);
                if (completed) source.run();
                stage.close(); xAfterClose[0] = stage.getX();
                if (!completed) source.run();
            });
            robot.interact(() -> {
                assertThat(reference.get().getX()).isEqualTo(xAfterClose[0]);
                if (!completed) assertThat(source.isCancelled()).isTrue();
                assertThat(reference.get().isShowing()).isFalse();
            });
        } finally {
            if (reference.get() != null) robot.interact(() -> { reference.get().close(); reference.get().setScene(null); });
        }
    }
}
