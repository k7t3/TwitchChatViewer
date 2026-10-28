package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.auth.AuthenticatorView;
import de.saxsys.mvvmfx.FluentViewLoader;
import com.github.k7t3.tcv.view.command.LoadCredentialCommand;
import atlantafx.base.controls.ModalPane;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class AuthenticatorLifecycleTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void rejectedClientAdoptionReleasesTheUnacceptedClientAndReportsFailure(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        var client = mock(Twitch.class);
        when(loader.load()).thenReturn(Optional.of(client));
        var attempted = new CountDownLatch(1);
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, value -> {
            attempted.countDown();
            throw new IllegalStateException("client was rejected by its owner");
        }, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        var loggedIn = new ArrayList<LoginEvent>();
        model.subscribe(LoginEvent.class, loggedIn::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(model::beginAuthentication);
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                assertThat(model.getError()).isNotBlank();
                assertThat(model.isBusy()).isFalse();
                assertThat(loggedIn).isEmpty();
            });
            var release = queue.poll(5, TimeUnit.SECONDS);
            assertThat(release).isNotNull();
            worker.submit(release).get(5, TimeUnit.SECONDS);
            verify(client).close();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void closingAfterClientAdoptionDoesNotCloseTheApplicationClient(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        var client = mock(Twitch.class);
        when(loader.load()).thenReturn(Optional.of(client));
        var adopted = new ArrayList<Twitch>();
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, adopted::add, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(model::beginAuthentication);
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(adopted).containsExactly(client));

            robot.interact(model::close);

            verify(client, never()).close();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void logoutCancelsAuthenticationWaitingInTheQueue(FxRobot robot) {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> mock(TwitchLoader.class), client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try {
            robot.interact(() -> {
                var load = model.loadClientAsync();

                model.onLogout();

                assertThat(load.isCancelled()).isTrue();
                assertThat(model.isBusy()).isFalse();
                assertThat(model.isClosed()).isFalse();
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void unsuccessfulDeviceFlowStopsWaitingAndReportsFailure(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenReturn(Optional.empty());
        var callback = new AtomicReference<Consumer<Optional<Twitch>>>();
        when(loader.startAuthenticate(any())).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return new TwitchLoader.DeviceFlow("CODE", "https://example.test", "https://example.test/complete");
        });
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(model::beginAuthentication);
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(model.isBusy()).isTrue());
            var flow = queue.poll(5, TimeUnit.SECONDS);
            assertThat(flow).isNotNull();
            worker.submit(flow).get(5, TimeUnit.SECONDS);

            worker.submit(() -> callback.get().accept(Optional.empty())).get(5, TimeUnit.SECONDS);

            robot.interact(() -> {
                assertThat(model.isBusy()).isFalse();
                assertThat(model.getError()).isNotBlank();
                assertThat(model.getAuthUri()).isNull();
            });
            verify(loader).close();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void authenticationViewShowsFailureAndCanRetry(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenThrow(new IllegalStateException("credential read failed"));
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        var view = new AtomicReference<Parent>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                view.set(FluentViewLoader.fxmlView(AuthenticatorView.class).codeBehind(new AuthenticatorView()).viewModel(model)
                        .resourceBundle(Resources.getResourceBundle()).load().getView());
                stage.setScene(new Scene(view.get(), 500, 400));
                stage.show();
                view.get().applyCss();
                model.beginAuthentication();
            });
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);

            robot.interact(() -> {
                var message = (Label) view.get().lookup("#errorLabel");
                assertThat(message).isNotNull();
                assertThat(message.isVisible()).isTrue();
                assertThat(message.getText()).isEqualTo(model.getError());
                var progress = (ProgressBar) view.get().lookup("#progressBar");
                assertThat(progress.getProgress()).isZero();
                var retry = (Button) view.get().lookup("#retryButton");
                assertThat(retry.isVisible()).isTrue();
            });
            var disposing = queue.poll(5, TimeUnit.SECONDS);
            assertThat(disposing).isNotNull();
            worker.submit(disposing).get(5, TimeUnit.SECONDS);
            verify(loader).awaitClosed();
            robot.interact(() -> {
                var retry = (Button) view.get().lookup("#retryButton");
                var message = (Label) view.get().lookup("#errorLabel");
                retry.fire();

                assertThat(queue).hasSize(1);
                assertThat(model.isBusy()).isTrue();
                assertThat(message.isVisible()).isFalse();
            });
            robot.interact(model::close);
            Runnable pending;
            while ((pending = queue.poll()) != null) worker.submit(pending).get(5, TimeUnit.SECONDS);
            model.resourcesReleased().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void closingAuthenticationClearsItsCommandRunningState(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> mock(TwitchLoader.class), client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        var commandReference = new AtomicReference<LoadCredentialCommand>();
        var container = new AtomicReference<StackPane>();
        try {
            robot.interact(() -> {
                var modal = new ModalPane();
                container.set(new StackPane(modal));
                stage.setScene(new Scene(container.get(), 640, 480)); stage.show(); modal.applyCss();
                var command = new LoadCredentialCommand(modal, new SimpleBooleanProperty(true), () -> model);
                commandReference.set(command);
                command.execute();
            });
            // 画面接続後に予約された開始を進めてから、実行中の認証を終了する。
            robot.interact(() -> {
                var command = commandReference.get();
                assertThat(command.isRunning()).isTrue();
                assertThat(queue).hasSize(1);
                model.close();
                assertThat(command.isRunning()).isFalse();
                assertThat(container.get().getChildren()).hasSize(1);
            });
        } finally {
            robot.interact(() -> {
                if (commandReference.get() != null) commandReference.get().close();
                model.close(); stage.hide(); stage.setScene(null);
            });
            Runnable pending;
            while ((pending = queue.poll()) != null) pending.run();
            model.resourcesReleased().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void repeatedLoginWhileQueuedStartsOnlyOneAttempt(FxRobot robot) {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> mock(TwitchLoader.class), client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try {
            robot.interact(() -> {
                model.beginAuthentication();
                model.beginAuthentication();

                assertThat(model.isBusy()).isTrue();
                assertThat(queue).hasSize(1);
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void rejectedCredentialLoadIsReportedWithoutLeavingAnActiveTask(FxRobot robot) {
        var loader = mock(TwitchLoader.class);
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, task -> {
            throw new RejectedExecutionException("worker is closed");
        }, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try {
            robot.interact(() -> {
                var task = model.loadClientAsync();

                assertThat(task.isCancelled()).isTrue();
                assertThat(model.getError()).isNotBlank();
                assertThat(model.isInitialized()).isFalse();
            });
            verifyNoInteractions(loader);
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void credentialReadFailureIsReportedAndReleasesLoader(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenThrow(new IllegalStateException("credential read failed"));
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadClientAsync(); });
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);

            robot.interact(() -> {
                assertThat(model.getError()).isNotBlank();
                assertThat(model.isInitialized()).isFalse();
            });
            verify(loader).close();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void credentialLoaderIsCreatedOffUiThread(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenReturn(Optional.empty());
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            return loader;
        }, client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadClientAsync(); });
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);

            robot.interact(() -> assertThat(model.isInitialized()).isTrue());
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void closingBeforeUiDeliveryDisposesLoadedClient(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        var client = mock(Twitch.class);
        when(loader.load()).thenReturn(Optional.of(client));
        var adopted = new ArrayList<Twitch>();
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, adopted::add, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.loadClientAsync();
                try {
                    var load = queue.poll(5, TimeUnit.SECONDS);
                    assertThat(load).isNotNull();
                    worker.submit(load).get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                model.close();
            });
            Runnable cleanup;
            while ((cleanup = queue.poll()) != null) worker.submit(cleanup).get(5, TimeUnit.SECONDS);

            robot.interact(() -> assertThat(adopted).isEmpty());
            verify(client).close();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void closingScreenCancelsQueuedCredentialLoad(FxRobot robot) {
        var loader = mock(TwitchLoader.class);
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        var task = new AtomicReference<FXTask<?>>();

        robot.interact(() -> {
            task.set(model.loadClientAsync());
            model.close();
        });

        assertThat(task.get().isCancelled()).isTrue();
        verifyNoInteractions(loader);
    }

    @Test
    void closingScreenClosesItsAuthenticationSession(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenReturn(Optional.empty());
        when(loader.startAuthenticate(any())).thenReturn(new TwitchLoader.DeviceFlow(
                "CODE", "https://example.test", "https://example.test/complete"));
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, queue::add, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadClientAsync(); });
            var load = queue.poll(5, TimeUnit.SECONDS);
            assertThat(load).isNotNull();
            worker.submit(load).get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(model.isInitialized()).isTrue());
            robot.interact(() -> { model.startAuthenticateAsync(); });
            var start = queue.poll(5, TimeUnit.SECONDS);
            assertThat(start).isNotNull();
            worker.submit(start).get(5, TimeUnit.SECONDS);

            robot.interact(model::close);

            verify(loader).close();
        } finally {
            robot.interact(model::close);
        }
    }
}
