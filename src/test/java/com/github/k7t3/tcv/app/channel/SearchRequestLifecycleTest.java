package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static com.github.k7t3.tcv.app.image.ImageTestDoubles.emptyLoader;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class SearchRequestLifecycleTest {
    @Test
    void applicationShutdownWaitsForTheAcceptedQueryOutsideFx(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        var worker = spy(new TaskWorker());
        var helper = new AppHelper(worker, new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        doAnswer(invocation -> { draining.countDown(); return invocation.callRealMethod(); }).when(worker).close();
        var api = mock(TwitchAPI.class);
        var client = mock(Twitch.class);
        when(client.getTwitchAPI()).thenReturn(api);
        when(api.search("owned query", true)).thenAnswer(invocation -> {
            entered.countDown();
            awaitIgnoringInterrupts(release);
            return List.of(found("late"));
        });
        var model = new SearchChannelViewModel(mock(ChannelViewModelRepository.class), emptyLoader(), helper.getExecutor(), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        try (var closing = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> {
                    model.setTwitch(client);
                    model.setKeyword("owned query");
                    model.search(TimeUnit.MILLISECONDS, 0);
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                var completed = closing.submit(helper::close);
                assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> assertThat(completed.isDone()).isFalse());
                release.countDown();
                completed.get(5, TimeUnit.SECONDS);
                robot.interact(() -> assertThat(model.getChannels()).isEmpty());
            } finally { release.countDown(); }
        } finally {
            robot.interact(model::close);
            helper.close();
        }
    }

    @Test
    void queuedSearchesKeepTheirOwnInputsAndOnlyTheLatestMayCallTheApi(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            when(fixture.api.search("latest", false)).thenReturn(List.of(found("latest")));
            robot.interact(() -> {
                fixture.model.setKeyword("obsolete");
                fixture.model.search(TimeUnit.MILLISECONDS, 0);
                fixture.model.setKeyword("latest");
                fixture.model.setOnlyLive(false);
                fixture.model.search(TimeUnit.MILLISECONDS, 0);
                fixture.model.setKeyword("changed after scheduling");
                fixture.model.setOnlyLive(true);
            });
            fixture.runQueued();
            fixture.runQueued();
            verify(fixture.api).search("latest", false);
            verify(fixture.api, never()).search("obsolete", true);
            fixture.assertResults("latest");
            assertThat(fixture.errors).isEmpty();
        }
    }

    @Test
    void aNonInterruptibleOldQueryCannotReplaceTheNewerResults(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture(robot)) {
            when(fixture.api.search("old", true)).thenAnswer(invocation -> {
                assertThat(Platform.isFxApplicationThread()).isFalse();
                entered.countDown();
                awaitIgnoringInterrupts(release);
                return List.of(found("old"));
            });
            when(fixture.api.search("latest", true)).thenReturn(List.of(found("latest")));
            fixture.search("old");
            var previous = fixture.startQueued();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                fixture.search("latest");
                fixture.runQueued();
                fixture.assertResults("latest");
            } finally { release.countDown(); }
            previous.get(5, TimeUnit.SECONDS);
            robot.interact(() -> {});
            fixture.assertResults("latest");
            assertThat(fixture.errors).isEmpty();
        } finally { release.countDown(); }
    }

    @Test
    void anApiFailureEndsLoadingAndAllowsAFreshRetry(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var failure = new IllegalStateException("search failed");
            when(fixture.api.search("retry", true)).thenThrow(failure).thenReturn(List.of(found("recovered")));
            fixture.search("retry");
            fixture.runQueued();
            assertThat(fixture.errors).containsExactly(failure);
            fixture.assertResults();
            robot.interact(() -> assertThat(fixture.model.isLoading()).isFalse());
            fixture.search("retry");
            fixture.runQueued();
            fixture.assertResults("recovered");
            assertThat(fixture.errors).containsExactly(failure);
        }
    }

    @Test
    void rejectedSubmissionEndsLoadingAndCanBeRetried(FxRobot robot) throws Exception {
        var rejecting = new AtomicBoolean(true);
        var failure = new RejectedExecutionException("worker is unavailable");
        try (var fixture = new Fixture(robot, emptyLoader(), queue -> task -> {
            if (rejecting.getAndSet(false)) throw failure;
            queue.add(task);
        })) {
            when(fixture.api.search("retry", true)).thenReturn(List.of(found("recovered")));
            fixture.search("retry");
            assertThat(fixture.errors).containsExactly(failure);
            robot.interact(() -> assertThat(fixture.model.isLoading()).isFalse());
            fixture.search("retry");
            fixture.runQueued();
            fixture.assertResults("recovered");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partialResultConstructionReleasesEveryCreatedImageAndAllowsRetry(boolean cleanupFails, FxRobot robot) throws Exception {
        var first = image();
        var broken = image();
        var failure = new IllegalStateException("cannot bind second image");
        var firstCleanup = new IllegalStateException("first image cleanup failed");
        var brokenCleanup = new IllegalStateException("second image cleanup failed");
        when(broken.valueProperty()).thenThrow(failure);
        if (cleanupFails) {
            doThrow(firstCleanup).when(first).close();
            doThrow(brokenCleanup).when(broken).close();
        }
        var images = emptyLoader();
        when(images.load("file:/first", 32, 32)).thenReturn(first);
        when(images.load("file:/broken", 32, 32)).thenReturn(broken);
        try (var fixture = new Fixture(robot, images, queue -> queue::add)) {
            when(fixture.api.search("first attempt", true)).thenReturn(List.of(found("first"), found("broken")));
            fixture.search("first attempt");
            fixture.runQueued();
            fixture.assertResults();
            assertThat(fixture.errors).containsExactly(failure);
            verify(first).close();
            verify(broken).close();
            if (cleanupFails) assertThat(failure.getSuppressed()).containsExactly(brokenCleanup, firstCleanup);
            when(fixture.api.search("retry", true)).thenReturn(List.of(found("recovered")));
            fixture.search("retry");
            fixture.runQueued();
            fixture.assertResults("recovered");
        }
    }

    @Test
    void closingWhileBuildingResultsReleasesTheUnadoptedImage(FxRobot robot) throws Exception {
        var reference = new AtomicReference<SearchChannelViewModel>();
        var image = image();
        var images = emptyLoader();
        when(images.load("file:/closing", 32, 32)).thenAnswer(invocation -> {
            reference.get().close();
            return image;
        });
        try (var fixture = new Fixture(robot, images, queue -> queue::add)) {
            reference.set(fixture.model);
            when(fixture.api.search("closing", true)).thenReturn(List.of(found("closing"), found("must-not-load")));
            fixture.search("closing");
            fixture.runQueued();
            fixture.assertResults();
            assertThat(fixture.model.isClosed()).isTrue();
            verify(image).close();
            verify(images, never()).load("file:/must-not-load", 32, 32);
            assertThat(fixture.errors).isEmpty();
        }
    }

    @Test
    void logoutCannotRestartTheOldSessionAndANewSessionCanSearch(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            fixture.search("old");
            robot.interact(fixture.model::onLogout);
            fixture.search("must not start");
            fixture.runQueued();
            assertThat(fixture.queue).isEmpty();
            verifyNoInteractions(fixture.api);
            var next = mock(Twitch.class);
            var nextApi = mock(TwitchAPI.class);
            when(next.getTwitchAPI()).thenReturn(nextApi);
            when(nextApi.search("new", true)).thenReturn(List.of(found("new")));
            robot.interact(() -> fixture.model.setTwitch(next));
            fixture.search("new");
            fixture.runQueued();
            fixture.assertResults("new");
        }
    }

    private static FoundChannel found(String id) {
        return new FoundChannel(new Broadcaster(id, id, id, "file:/" + id, ""), true, "Game");
    }

    private static LazyImage image() {
        var image = mock(LazyImage.class);
        when(image.valueProperty()).thenReturn(new ReadOnlyObjectWrapper<Image>().getReadOnlyProperty());
        return image;
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) throws Exception {
        while (true) {
            try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); return; }
            catch (InterruptedException ignored) { }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final java.util.concurrent.ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final Twitch client = mock(Twitch.class);
        final TwitchAPI api = mock(TwitchAPI.class);
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final SearchChannelViewModel model;
        final FxRobot robot;

        Fixture(FxRobot robot) { this(robot, emptyLoader(), queue -> queue::add); }
        Fixture(FxRobot robot, ImageLoader images, Function<BlockingQueue<Runnable>, Executor> executor) {
            this.robot = robot;
            when(client.getTwitchAPI()).thenReturn(api);
            model = new SearchChannelViewModel(mock(ChannelViewModelRepository.class), images, executor.apply(queue), failure -> {
                assertThat(Platform.isFxApplicationThread()).isTrue();
                errors.add(failure);
            });
            robot.interact(() -> model.setTwitch(client));
        }
        void search(String keyword) {
            robot.interact(() -> { model.setKeyword(keyword); model.search(TimeUnit.MILLISECONDS, 0); });
        }
        Future<?> startQueued() throws Exception {
            var task = queue.poll(5, TimeUnit.SECONDS);
            assertThat(task).isNotNull();
            return workers.submit(task);
        }
        void runQueued() throws Exception {
            startQueued().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {});
        }
        void assertResults(String... ids) {
            robot.interact(() -> assertThat(model.getChannels()).extracting(item -> item.getBroadcaster().getUserId()).containsExactly(ids));
        }
        @Override public void close() {
            try { robot.interact(model::close); }
            finally { workers.close(); }
        }
    }
}
