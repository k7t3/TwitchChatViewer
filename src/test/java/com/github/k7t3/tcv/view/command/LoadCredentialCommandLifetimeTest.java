package com.github.k7t3.tcv.view.command;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.ArrayList;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class LoadCredentialCommandLifetimeTest {
    @Test
    void closingTheCommandReleasesItsAuthenticationAndRunningBinding(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.command.execute(); f.busy.set(true);
                assertThat(f.pane.getPersistent()).isTrue(); assertThat(f.command.isRunning()).isTrue();
                f.command.close(); f.command.close();
                assertThat(f.models.getFirst().isClosed()).isTrue();
                assertThat(f.pane.getContent()).isNull(); assertThat(f.pane.isDisplay()).isFalse();
                assertThat(f.command.isRunning()).isFalse(); assertThat(f.command.isExecutable()).isFalse();
                f.allowed.set(false); f.allowed.set(true);
                assertThat(f.command.isExecutable()).isFalse();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void hidingOrReplacingTheModalEndsItsSessionWithoutHidingTheSuccessor(boolean replace, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.command.execute(); var successor = new Pane();
                if (replace) f.pane.show(successor); else f.pane.hide();
                assertThat(f.models.getFirst().isClosed()).isTrue();
                f.command.close();
                if (replace) { assertThat(f.pane.getContent()).isSameAs(successor); assertThat(f.pane.isDisplay()).isTrue(); }
            }
        });
    }

    @Test
    void becomingAuthorizedImmediatelyReleasesTheAuthenticationView(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.command.execute(); f.allowed.set(false);
                assertThat(f.models.getFirst().isClosed()).isTrue(); assertThat(f.pane.isDisplay()).isFalse();
                f.command.execute(); assertThat(f.models).hasSize(1);
            }
        });
    }

    @Test
    void closingBeforeTheAfterShownCallbackDoesNotBeginAuthentication(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.command.execute(); f.command.close();
                verify(f.models.getFirst(), never()).beginAuthentication();
            }
        });
    }

    @Test
    void aModelReturnedAfterItsCommandWasClosedIsReleasedWithoutLoadingControls(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.created = model -> f.command.close();
                f.command.execute();
                assertThat(f.models.getFirst().isClosed()).isTrue(); assertThat(f.pane.isDisplay()).isFalse();
                verify(f.models.getFirst(), never()).initializedProperty();
                verify(f.models.getFirst(), never()).beginAuthentication();
            }
        });
    }

    @Test
    void reentrantOpeningSharesTheExistingCreation(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                f.created = model -> { if (f.models.size() == 1) f.command.execute(); };
                f.command.execute(); assertThat(f.models).hasSize(1);
            }
        });
    }

    @Test
    void aPartialFxmlFailureKeepsItsOriginalCauseWhenCleanupAlsoFails(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture()) {
                var cleanup = new IllegalArgumentException("cleanup failed");
                f.created = model -> {
                    doThrow(new IllegalStateException("FXML fixture failure")).when(model).initializedProperty();
                    doThrow(cleanup).when(model).close();
                };
                assertThatThrownBy(f.command::execute).hasStackTraceContaining("FXML fixture failure")
                        .satisfies(failure -> assertThat(failure.getSuppressed()).contains(cleanup));
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final ModalPane pane = new ModalPane();
        final SimpleBooleanProperty allowed = new SimpleBooleanProperty(true);
        final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
        final ArrayList<AuthenticatorViewModel> models = new ArrayList<>();
        final ArrayList<AuthenticatorViewModel> originals = new ArrayList<>();
        final Stage stage = new Stage();
        Consumer<AuthenticatorViewModel> created = model -> {};
        final LoadCredentialCommand command;
        Fixture() {
            stage.setScene(new Scene(new StackPane(pane), 640, 480)); stage.show(); pane.applyCss();
            command = new LoadCredentialCommand(pane, allowed, () -> {
                var original = new AuthenticatorViewModel(mock(CredentialStore.class), client -> {}, Runnable::run, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
                original.delegateLifecycleToOwner(); originals.add(original);
                var model = spy(original); models.add(model);
                doNothing().when(model).beginAuthentication();
                doReturn(busy.getReadOnlyProperty()).when(model).busyProperty();
                created.accept(model); return model;
            });
        }
        @Override public void close() {
            try { command.close(); }
            finally { originals.forEach(AuthenticatorViewModel::close); stage.close(); stage.setScene(null); }
        }
    }
}
