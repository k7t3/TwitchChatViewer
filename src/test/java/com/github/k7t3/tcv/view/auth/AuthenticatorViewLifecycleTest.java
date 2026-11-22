package com.github.k7t3.tcv.view.auth;

import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class AuthenticatorViewLifecycleTest {
    @Test
    void detachingTheAuthenticationViewAlsoDetachesItsControls(FxRobot robot) {
        var model = new AuthenticatorViewModel(mock(CredentialStore.class), client -> {}, Runnable::run, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try {
            robot.interact(() -> {
                var view = FluentViewLoader.fxmlView(AuthenticatorView.class).codeBehind(new AuthenticatorView())
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load().getView();
                var parent = new StackPane(view);
                new javafx.scene.Scene(parent); parent.applyCss();
                var link = (javafx.scene.control.Hyperlink) view.lookup("#authUriLink");
                var code = (javafx.scene.control.TextField) view.lookup("#userCodeField");
                var image = (javafx.scene.image.ImageView) view.lookup("#qrcodeImageView");
                var progress = (javafx.scene.control.ProgressBar) view.lookup("#progressBar");
                var retry = (javafx.scene.control.Button) view.lookup("#retryButton");
                parent.getChildren().clear();
                assertThat(model.isClosed()).isTrue();
                assertThat(link.textProperty().isBound()).isFalse();
                assertThat(link.disableProperty().isBound()).isFalse();
                assertThat(code.textProperty().isBound()).isFalse();
                assertThat(image.imageProperty().isBound()).isFalse();
                assertThat(image.getImage()).isNull();
                assertThat(progress.progressProperty().isBound()).isFalse();
                assertThat(retry.getOnAction()).isNull();
            });
        } finally { robot.interact(model::close); }
    }

    @Test
    void anActionCapturedBeforeDetachmentCannotRestartItsClosedSession(FxRobot robot) {
        var model = spy(new AuthenticatorViewModel(mock(CredentialStore.class), client -> {}, Runnable::run, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); }));
        doNothing().when(model).beginAuthentication();
        try {
            robot.interact(() -> {
                var view = FluentViewLoader.fxmlView(AuthenticatorView.class).codeBehind(new AuthenticatorView())
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load().getView();
                var parent = new StackPane(view);
                new javafx.scene.Scene(parent); parent.applyCss();
                var retry = (javafx.scene.control.Button) view.lookup("#retryButton");
                var callback = retry.getOnAction(); parent.getChildren().clear();
                callback.handle(new javafx.event.ActionEvent());
                verify(model, never()).beginAuthentication();
            });
        } finally { robot.interact(model::close); }
    }

    @Test
    void removingAuthenticationViewClosesItsSession(FxRobot robot) {
        var model = new AuthenticatorViewModel(mock(CredentialStore.class), client -> {}, Runnable::run, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try {
            robot.interact(() -> {
                var view = FluentViewLoader.fxmlView(AuthenticatorView.class).codeBehind(new AuthenticatorView())
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load().getView();
                var container = new StackPane(view);

                container.getChildren().clear();

                assertThat(model.isClosed()).isTrue();
            });
        } finally {
            robot.interact(model::close);
        }
    }
}
