package com.github.k7t3.tcv.view.main;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class AuthenticationMenuItemLifetimeTest {
    @Test
    void closingTheItemDetachesItsBindingsAndRetainedAction(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                var source = new SimpleBooleanProperty(); f.item.authorizedProperty().bind(source);
                var action = f.item.getOnAction(); f.item.close(); f.item.close(); source.set(true);
                assertThat(f.item.authorizedProperty().isBound()).isFalse(); assertThat(f.item.isAuthorized()).isFalse();
                assertThat(f.item.textProperty().isBound()).isFalse(); assertThat(f.item.graphicProperty().isBound()).isFalse();
                assertThat(f.item.getOnAction()).isNull(); f.item.fire(); action.handle(new javafx.event.ActionEvent());
                verify(f.model, never()).createAuthenticator(any()); verify(f.model, never()).logout();
            }
        });
    }

    @Test
    void closingDuringConfirmationRejectsALateAcceptedLogout(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.item.authorizedProperty().set(true);
                when(f.alert.showAndWait()).thenAnswer(call -> { f.item.close(); return Optional.of(ButtonType.OK); });
                f.item.fire(); verify(f.alert).close(); verify(f.model, never()).logout(); verify(f.model, never()).close();
            }
        });
    }

    @Test
    void aConfirmationReturnedAfterItsOwnerClosedIsNeverShown(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.item.authorizedProperty().set(true); f.factory = () -> { f.item.close(); return f.alert; };
                f.item.fire(); verify(f.alert, never()).showAndWait(); verify(f.alert).close(); verify(f.model, never()).logout();
            }
        });
    }

    @Test
    void changingAuthorizationCancelsTheOldAnswerAndAllowsANewConfirmation(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.item.authorizedProperty().set(true);
                when(f.alert.showAndWait()).thenAnswer(call -> {
                    f.item.authorizedProperty().set(false); f.item.authorizedProperty().set(true); return Optional.of(ButtonType.OK);
                }).thenReturn(Optional.of(ButtonType.OK));
                f.item.fire(); verify(f.model, never()).logout();
                f.item.fire(); verify(f.model).logout(); verify(f.alert, times(2)).close();
            }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewLoginCancelsTheOldConfirmationEvenWhenAuthorizationStaysTrue(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                var observer = org.mockito.ArgumentCaptor.forClass(TypeSafeNotificationObserver.class);
                verify(f.model).subscribe(eq(LoginEvent.class), observer.capture());
                f.item.authorizedProperty().set(true);
                when(f.alert.showAndWait()).thenAnswer(call -> {
                    observer.getValue().receivedNotification(new LoginEvent()); return Optional.of(ButtonType.OK);
                });
                f.item.fire(); verify(f.model, never()).logout(); verify(f.alert).close();
                f.item.close(); verify(f.model).unsubscribe(eq(LoginEvent.class), same(observer.getValue()));
            }
        });
    }

    @Test
    void reentrantConfirmationActionsSubmitOneLogout(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.item.authorizedProperty().set(true);
                f.factory = () -> { if (f.factories.get() == 1) f.item.fire(); return f.alert; };
                when(f.alert.showAndWait()).thenReturn(Optional.of(ButtonType.OK));
                f.item.fire(); assertThat(f.factories).hasValue(1); verify(f.model).logout(); verify(f.alert).close();
            }
        });
    }

    @Test
    void closingTheMenuItemAlsoClosesItsLoginPanel(FxRobot robot) {
        robot.interact(() -> {
            var model = spy(new AuthenticatorViewModel(mock(CredentialStore.class), client -> {}, Runnable::run, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); }));
            doNothing().when(model).beginAuthentication();
            try (var f = new Fixture()) {
                when(f.model.createAuthenticator(any())).thenReturn(model);
                f.item.fire(); assertThat(f.pane.isDisplay()).isTrue();
                f.item.close(); assertThat(model.isClosed()).isTrue(); assertThat(f.pane.isDisplay()).isFalse();
            } finally { model.close(); }
        });
    }

    @Test
    void closingTheOwnerEndsTheRealLogoutModalLoop(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                var alert = new Alert(Alert.AlertType.CONFIRMATION);
                var fallback = new java.util.concurrent.atomic.AtomicBoolean();
                var guard = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
                guard.setOnFinished(event -> { fallback.set(true); alert.close(); });
                alert.setOnShown(event -> javafx.application.Platform.runLater(f.item::close));
                f.factory = () -> alert; f.item.authorizedProperty().set(true);
                try {
                    guard.play(); f.item.fire(); assertThat(fallback).isFalse(); assertThat(alert.isShowing()).isFalse();
                    verify(f.model, never()).logout();
                } finally { guard.stop(); alert.close(); }
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final MainViewModel model = mock(MainViewModel.class);
        final ModalPane pane = new ModalPane();
        final StackPane root = new StackPane(pane);
        final Stage stage = new Stage();
        final Alert alert = mock(Alert.class);
        final AtomicInteger factories = new AtomicInteger();
        Supplier<Alert> factory = () -> alert;
        final AuthenticationMenuItem item;
        Fixture() {
            var dialogPane = new DialogPane(); when(alert.getDialogPane()).thenReturn(dialogPane);
            when(alert.getButtonTypes()).thenReturn(dialogPane.getButtonTypes()); when(alert.showAndWait()).thenReturn(Optional.empty());
            stage.setScene(new Scene(root, 640, 480)); stage.show(); pane.applyCss();
            item = new AuthenticationMenuItem(pane, mock(CredentialStore.class), root, model, () -> {
                factories.incrementAndGet(); return factory.get();
            });
        }
        @Override public void close() { item.close(); stage.close(); stage.setScene(null); }
    }
}
