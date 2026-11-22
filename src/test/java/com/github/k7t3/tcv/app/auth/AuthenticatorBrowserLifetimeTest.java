package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.TwitchLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class AuthenticatorBrowserLifetimeTest {
    @Test
    void authenticationReleaseWaitsForItsBrowserCallEvenWhenTheDesktopIgnoresCancellation(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenReturn(Optional.empty());
        when(loader.startAuthenticate(any())).thenReturn(new TwitchLoader.DeviceFlow("CODE", "https://example.invalid/verify", "https://example.invalid/complete"));
        var desktop = mock(Desktop.class);
        when(desktop.isSupported(Desktop.Action.BROWSE)).thenReturn(true);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(ignored -> {
            entered.countDown();
            for (;;) { try { release.await(); break; } catch (InterruptedException ignoredInterrupt) {} }
            return null;
        }).when(desktop).browse(any());
        var queue = new LinkedBlockingQueue<Runnable>();
        Executor executor = task -> queue.add(() -> {
            try (var adapter = mockStatic(Desktop.class)) {
                adapter.when(Desktop::getDesktop).thenReturn(desktop);
                task.run();
            }
        });
        var model = new AuthenticatorViewModel(() -> loader, ignored -> {}, executor, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        try (var worker = Executors.newSingleThreadExecutor()) {
            robot.interact(model::loadClientAsync); runNext(queue);
            robot.interact(model::startAuthenticateAsync); runNext(queue);
            robot.interact(() -> assertThat(model.getAuthUri()).isEqualTo("https://example.invalid/complete"));
            runNext(queue); // QR code
            robot.interact(model::openAuthUri);
            var opening = worker.submit(() -> runNext(queue));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                assertThat(model.resourcesReleased().toCompletableFuture().isDone()).isFalse();
            } finally { release.countDown(); }
            opening.get(5, TimeUnit.SECONDS);
            Runnable cleanup; while ((cleanup = queue.poll()) != null) cleanup.run();
            model.resourcesReleased().toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(desktop).browse(URI.create("https://example.invalid/complete"));
        } finally {
            release.countDown(); robot.interact(model::close);
            Runnable cleanup; while ((cleanup = queue.poll()) != null) cleanup.run();
        }
    }

    private static void runNext(LinkedBlockingQueue<Runnable> queue) {
        Objects.requireNonNull(queue.poll(), "expected owned authentication work").run();
    }
}
