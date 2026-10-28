package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ExceptionHandlerOwnerTest {
    @Test
    void failedDialogDisplayReleasesTheOwnerCloseHandler(FxRobot robot) {
        robot.interact(() -> {
            var owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 320, 200));
            owner.show();
            var displayFailure = new IllegalStateException("dialog display failed");
            try (var helper = mockConstruction(AppHelper.class);
                 var alerts = mockConstruction(Alert.class, (alert, context) -> {
                     when(alert.showAndWait()).thenThrow(displayFailure);
                     when(alert.getDialogPane()).thenReturn(new DialogPane());
                 })) {

                assertThatThrownBy(() -> ExceptionHandler.handle(owner, new IllegalStateException("operation failed")))
                        .isSameAs(displayFailure);
                owner.close();

                assertThat(alerts.constructed()).hasSize(1);
                verify(alerts.constructed().getFirst(), never()).close();
                assertThat(helper.constructed()).isEmpty();
            } finally {
                owner.close();
            }
        });
    }

    @ParameterizedTest(name = "real error dialog closes with owner, invalidCredential={0}")
    @ValueSource(booleans = {false, true})
    void realDialogDoesNotOutliveItsOwner(boolean invalidCredential, FxRobot robot) {
        robot.interact(() -> {
            var owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 320, 200));
            owner.show();
            var shown = new AtomicReference<javafx.stage.Window>();
            var fallback = new AtomicBoolean();
            var guard = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            guard.setOnFinished(event -> {
                fallback.set(true);
                if (shown.get() != null) shown.get().hide();
                owner.close();
            });
            javafx.collections.ListChangeListener<javafx.stage.Window> windows = change -> {
                while (change.next()) {
                    for (var window : change.getAddedSubList()) {
                        if (window instanceof Stage stage && stage.getOwner() == owner) {
                            shown.set(window);
                            javafx.application.Platform.runLater(owner::close);
                        }
                    }
                }
            };
            javafx.stage.Window.getWindows().addListener(windows);
            try (var helper = mockConstruction(AppHelper.class)) {
                var failure = invalidCredential ? new InvalidCredentialException() : new IllegalStateException("operation failed");
                guard.play();

                ExceptionHandler.handle(owner, failure);

                assertThat(shown.get()).isNotNull();
                assertThat(fallback.get()).isFalse();
                assertThat(shown.get().isShowing()).isFalse();
                assertThat(owner.isShowing()).isFalse();
                assertThat(helper.constructed()).isEmpty();
            } finally {
                guard.stop();
                javafx.stage.Window.getWindows().removeListener(windows);
                if (shown.get() != null) shown.get().hide();
                owner.close();
            }
        });
    }

    @Test
    void credentialFailureTerminatesTheApplicationThatOwnsTheSuppliedChild(FxRobot robot) {
        robot.interact(() -> {
            var application = new Stage();
            var child = new Stage();
            application.setScene(new Scene(new StackPane(), 320, 200));
            child.initOwner(application);
            child.setScene(new Scene(new StackPane(), 240, 160));
            application.show();
            child.show();
            try (var helper = mockConstruction(AppHelper.class);
                 var alerts = mockConstruction(Alert.class, (alert, context) ->
                         when(alert.showAndWait()).thenReturn(Optional.of(ButtonType.OK)))) {

                ExceptionHandler.handle(child, new InvalidCredentialException());

                assertThat(alerts.constructed()).hasSize(1);
                verify(alerts.constructed().getFirst()).initOwner(application);
                assertThat(application.isShowing()).isFalse();
                assertThat(helper.constructed()).isEmpty();
            } finally {
                child.close();
                application.close();
            }
        });
    }

    @Test
    void unavailableExplicitOwnerCannotResolveOrTerminateAnotherApplication(FxRobot robot) {
        robot.interact(() -> {
            try (var helper = mockConstruction(AppHelper.class);
                 var alerts = mockConstruction(Alert.class, (alert, context) ->
                         when(alert.showAndWait()).thenReturn(Optional.of(ButtonType.OK)))) {

                assertThatCode(() -> ExceptionHandler.handle(null, new InvalidCredentialException())).doesNotThrowAnyException();

                assertThat(alerts.constructed()).hasSize(1);
                verify(alerts.constructed().getFirst(), never()).initOwner(any());
                verify(alerts.constructed().getFirst()).showAndWait();
                assertThat(helper.constructed()).isEmpty();
            }
        });
    }

    @ParameterizedTest(name = "explicit error owner, invalidCredential={0}")
    @ValueSource(booleans = {false, true})
    void explicitOwnerControlsTheDialogAndCredentialTermination(boolean invalidCredential, FxRobot robot) {
        robot.interact(() -> {
            var owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 320, 200));
            owner.show();
            try (var helper = mockConstruction(AppHelper.class);
                 var alerts = mockConstruction(Alert.class, (alert, context) -> {
                     when(alert.showAndWait()).thenReturn(Optional.of(ButtonType.OK));
                     when(alert.getDialogPane()).thenReturn(new DialogPane());
                 })) {
                var failure = invalidCredential ? new InvalidCredentialException() : new IllegalStateException("operation failed");

                assertThatCode(() -> ExceptionHandler.handle(owner, failure)).doesNotThrowAnyException();

                assertThat(alerts.constructed()).hasSize(1);
                var alert = alerts.constructed().getFirst();
                verify(alert).initOwner(owner);
                verify(alert).showAndWait();
                assertThat(helper.constructed()).isEmpty();
                assertThat(owner.isShowing()).isEqualTo(!invalidCredential);
            } finally {
                owner.close();
            }
        });
    }
}
