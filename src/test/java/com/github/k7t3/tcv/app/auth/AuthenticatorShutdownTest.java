package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchLoader;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class AuthenticatorShutdownTest {
    @Test
    void closingTheViewReturnsOnFxWhileTheApplicationOwnsThePollingTermination(FxRobot robot) throws Exception {
        var awaiting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenReturn(Optional.empty());
        doAnswer(invocation -> { assertThat(Platform.isFxApplicationThread()).isFalse(); awaiting.countDown(); await(release); return null; })
                .when(loader).awaitClosed();
        var worker = spy(new TaskWorker());
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var draining = new CountDownLatch(1);
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, helper.getExecutor(), unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        helper.awaitOnShutdown(model.resourcesReleased());
        var loaded = new CountDownLatch(1);
        try (var closing = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> model.loadClientAsync().onDone(value -> loaded.countDown()));
                assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                assertThat(awaiting.await(5, TimeUnit.SECONDS)).isTrue();
                var observer = model.resourcesReleased().toCompletableFuture();
                observer.cancel(false);
                assertThat(model.resourcesReleased().toCompletableFuture().isDone()).isFalse();
                var completed = closing.submit(helper::close);
                assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> assertThat(completed.isDone()).isFalse());
                verify(publishers, never()).close();
                release.countDown();
                completed.get(5, TimeUnit.SECONDS);
                model.resourcesReleased().toCompletableFuture().get(5, TimeUnit.SECONDS);
                verify(loader).close();
                verify(loader).awaitClosed();
                verify(publishers).close();
            } finally { release.countDown(); }
        } finally { robot.interact(model::close); helper.close(); }
    }

    @Test
    void rejectedCleanupUsesBackgroundFallbackAndTheApplicationStillWaitsForIt(FxRobot robot) throws Exception {
        var rejecting = new AtomicBoolean();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loader = mock(TwitchLoader.class);
        var client = mock(Twitch.class);
        when(loader.load()).thenAnswer(invocation -> { rejecting.set(true); return Optional.of(client); });
        doAnswer(invocation -> { assertThat(Platform.isFxApplicationThread()).isFalse(); entered.countDown(); await(release); return null; })
                .when(client).close();
        var helper = new AppHelper(new TaskWorker(), new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var executor = helper.getExecutor();
        var model = new AuthenticatorViewModel(() -> loader, value -> { throw new IllegalStateException("client rejected"); }, task -> {
            if (rejecting.get()) throw new java.util.concurrent.RejectedExecutionException("application worker is stopping");
            executor.execute(task);
        }, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        helper.awaitOnShutdown(model.resourcesReleased());
        try (var closing = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(model::beginAuthentication);
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                var completed = closing.submit(helper::close);
                robot.interact(() -> assertThat(model.resourcesReleased().toCompletableFuture().isDone()).isFalse());
                release.countDown();
                completed.get(5, TimeUnit.SECONDS);
                verify(client).close();
                verify(loader).awaitClosed();
            } finally { release.countDown(); }
        } finally { robot.interact(model::close); helper.close(); }
    }

    @Test
    void closeFailuresResetTheUiAndArePreservedThroughBackgroundRelease(FxRobot robot) throws Exception {
        var loader = mock(TwitchLoader.class);
        when(loader.load()).thenReturn(Optional.empty());
        var primary = new IllegalStateException("stop request failed");
        var later = new IllegalStateException("polling cleanup failed");
        doThrow(primary).when(loader).close();
        doThrow(later).when(loader).awaitClosed();
        var publishers = mock(EventPublishers.class);
        var helper = new AppHelper(new TaskWorker(), publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var model = new AuthenticatorViewModel(() -> loader, client -> {}, helper.getExecutor(), unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        helper.awaitOnShutdown(model.resourcesReleased());
        var loaded = new CountDownLatch(1);
        try {
            robot.interact(() -> model.loadClientAsync().onDone(value -> loaded.countDown()));
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                assertThatThrownBy(model::close).isSameAs(primary);
                assertThat(model.isBusy()).isFalse();
                assertThat(model.isInitialized()).isFalse();
                assertThat(model.getAuthUri()).isNull();
            });
            assertThatThrownBy(() -> model.resourcesReleased().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(primary);
            assertThat(primary.getSuppressed()).containsExactly(later);
            assertThatThrownBy(helper::close).isSameAs(primary);
            verify(publishers).close();
        } finally {
            robot.interact(model::close);
            try { helper.close(); } catch (IllegalStateException expected) { assertThat(expected).isSameAs(primary); }
        }
    }

    @Test
    void aLoaderCreatedAfterViewCloseIsStillOwnedUntilItsPollingHasExited(FxRobot robot) throws Exception {
        var creating = new CountDownLatch(1);
        var finishCreation = new CountDownLatch(1);
        var awaiting = new CountDownLatch(1);
        var finishPolling = new CountDownLatch(1);
        var loader = mock(TwitchLoader.class);
        doAnswer(invocation -> { awaiting.countDown(); await(finishPolling); return null; }).when(loader).awaitClosed();
        var helper = new AppHelper(new TaskWorker(), new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var model = new AuthenticatorViewModel(() -> { creating.countDown(); await(finishCreation); return loader; }, client -> {}, helper.getExecutor(), unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); });
        helper.awaitOnShutdown(model.resourcesReleased());
        try (var closing = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(model::beginAuthentication);
                assertThat(creating.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                assertThat(model.resourcesReleased().toCompletableFuture().isDone()).isFalse();
                finishCreation.countDown();
                assertThat(awaiting.await(5, TimeUnit.SECONDS)).isTrue();
                var completed = closing.submit(helper::close);
                robot.interact(() -> assertThat(completed.isDone()).isFalse());
                finishPolling.countDown();
                completed.get(5, TimeUnit.SECONDS);
                verify(loader, never()).load();
                verify(loader).close();
                verify(loader).awaitClosed();
            } finally { finishCreation.countDown(); finishPolling.countDown(); }
        } finally { robot.interact(model::close); helper.close(); }
    }

    private static void await(CountDownLatch latch) {
        while (true) {
            try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); return; }
            catch (InterruptedException ignored) { }
        }
    }
}
