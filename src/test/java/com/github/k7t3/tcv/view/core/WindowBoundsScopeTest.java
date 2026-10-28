package com.github.k7t3.tcv.view.core;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class WindowBoundsScopeTest {
    @Test
    void aFailedRestoreDisplaysItsErrorOnTheWindowWhoseBoundsWereRequested(FxRobot robot) {
        var failure = new IllegalStateException("bounds unavailable");
        var source = FXTask.<WindowBounds>task(() -> { throw failure; });
        source.run();
        assertThatThrownBy(() -> source.get(5, TimeUnit.SECONDS)).hasCause(failure);
        var service = mock(WindowBoundsService.class);
        when(service.getBoundsAsync("room")).thenReturn(source);

        robot.interact(() -> {
            var stage = new FloatableStage(new Pane());
            try (var helper = mockConstruction(AppHelper.class);
                 var alerts = mockConstruction(Alert.class, (alert, context) -> {
                     when(alert.getDialogPane()).thenReturn(new DialogPane());
                     when(alert.showAndWait()).thenReturn(Optional.empty());
                 })) {

                new WindowBoundsScope(stage, "room", service);

                assertThat(alerts.constructed()).hasSize(1);
                verify(alerts.constructed().getFirst()).initOwner(stage);
                verify(alerts.constructed().getFirst()).showAndWait();
                assertThat(helper.constructed()).isEmpty();
            } finally {
                stage.close();
                stage.setScene(null);
            }
        });
    }

    @Test
    void anActiveWindowRestoresAndSavesItsLastBoundsOnceWithoutReplacingOtherHandlers(FxRobot robot) throws Exception {
        var stage = new FloatableStage[] {null};
        var source = FXTask.task(() -> new WindowBounds(160, 140, 360, 420, false));
        var service = mock(WindowBoundsService.class); when(service.getBoundsAsync("room")).thenReturn(source);
        var hiding = new AtomicInteger();
        robot.interact(() -> {
            stage[0] = new FloatableStage(new Pane());
            stage[0].setOnHiding(event -> hiding.incrementAndGet());
            new WindowBoundsScope(stage[0], "room", service);
            stage[0].show();
        });
        try {
            source.run(); source.get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(stage[0].getX()).isEqualTo(160);
                stage[0].setX(180); stage[0].close(); stage[0].close();
                var saved = ArgumentCaptor.forClass(WindowBounds.class);
                verify(service).saveBoundsAsync(eq("room"), saved.capture());
                assertThat(saved.getValue().x()).isEqualTo(180);
                assertThat(hiding).hasValue(1);
                stage[0].setX(300);
                assertThat(saved.getValue().x()).isEqualTo(180);
            });
        } finally { robot.interact(() -> { stage[0].close(); stage[0].setScene(null); }); }
    }

    @Test
    void closingFromARestorePropertyNotificationStopsTheRemainingBoundsChanges(FxRobot robot) throws Exception {
        var stage = new FloatableStage[] {null};
        var source = FXTask.task(() -> new WindowBounds(160, 140, 360, 420, false));
        var service = mock(WindowBoundsService.class); when(service.getBoundsAsync("room")).thenReturn(source);
        var closedY = new double[1];
        robot.interact(() -> {
            stage[0] = new FloatableStage(new Pane()); stage[0].show(); stage[0].setY(90);
            new WindowBoundsScope(stage[0], "room", service);
            stage[0].xProperty().addListener((observable, old, value) -> {
                if (value.doubleValue() == 160) { stage[0].close(); closedY[0] = stage[0].getY(); }
            });
        });
        try {
            source.run(); source.get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(stage[0].isShowing()).isFalse();
                assertThat(stage[0].getY()).isEqualTo(closedY[0]);
            });
        } finally { robot.interact(() -> { stage[0].close(); stage[0].setScene(null); }); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void onlyAnActiveWindowReceivesItsRestoreFailure(boolean closed, FxRobot robot) {
        var errors = new ArrayList<Throwable>();
        var failure = new IllegalStateException("bounds unavailable");
        var source = FXTask.<WindowBounds>task(() -> { throw failure; });
        var service = mock(WindowBoundsService.class); when(service.getBoundsAsync("room")).thenReturn(source);
        var stage = new FloatableStage[] {null};
        robot.interact(() -> {
            stage[0] = new FloatableStage(new Pane());
            new WindowBoundsScope(stage[0], "room", service, errors::add);
            source.run();
            if (closed) stage[0].close();
        });
        try {
            robot.interact(() -> { if (closed) assertThat(errors).isEmpty(); else assertThat(errors).containsExactly(failure); });
        } finally { robot.interact(() -> { stage[0].close(); stage[0].setScene(null); }); }
    }

    @Test
    void aWindowClosedBeforeRegistrationCannotStartAnotherRestore(FxRobot robot) {
        robot.interact(() -> {
            var stage = new FloatableStage(new Pane()); var service = mock(WindowBoundsService.class);
            stage.close(); new WindowBoundsScope(stage, "room", service);
            verifyNoInteractions(service); stage.setScene(null);
        });
    }
}
