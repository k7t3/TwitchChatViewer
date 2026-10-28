package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import com.github.k7t3.tcv.support.ClipEventSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PostedClipRetryTest {
    @Test
    void repeatedRetriesShareOneRequestAndPublishOnlyAfterTheCapturedLookup(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                var item = f.source.post("clip", f.before);
                f.item = item;
                f.result = item.retry();
                assertThat(item.retry()).isSameAs(f.result);
                assertThat(item.getClip()).isSameAs(f.before);
            });
            assertThat(f.queue).hasSize(1);
            f.runNext(); f.result.get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(f.item.getClip()).isSameAs(f.after));
            assertThat(f.calls).containsExactly(f.twitch);
            assertThat(f.urls).containsExactly("https://clips.twitch.tv/clip");
        }
    }

    @Test
    void removingAnItemCancelsItsQueuedRetryAndRejectsFurtherWork(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before); f.result = f.item.retry();
                f.item.remove();
                assertThat(f.result.isCancelled()).isTrue();
                assertThatThrownBy(f.item::retry).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(f.item::getThumbnailImage).isInstanceOf(IllegalStateException.class);
                assertThat(f.repository.getItems()).isEmpty();
            });
            f.runNext(); assertThat(f.calls).isEmpty();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void endingTheScopeCancelsRequestsAndRejectsNewImages(boolean close, FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before); f.result = f.item.retry();
                if (close) f.repository.close(); else f.repository.onLogout();
                assertThat(f.result.isCancelled()).isTrue();
                assertThat(f.repository.getItems()).isEmpty();
                assertThatThrownBy(() -> f.repository.getBroadcasterImage(f.owner)).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(f.item::getThumbnailImage).isInstanceOf(IllegalStateException.class);
            });
            f.runNext(); assertThat(f.calls).isEmpty();
        }
    }

    @Test
    void cancellationAllowsANewRetryBeforeTheOldTerminalEventIsDelivered(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before);
                var first = f.item.retry(); first.cancel(true);
                f.result = f.item.retry(); assertThat(f.result).isNotSameAs(first);
            });
            f.runNext(); f.runNext(); f.result.get(5, TimeUnit.SECONDS);
            assertThat(f.calls).hasSize(1);
            robot.interact(() -> assertThat(f.item.getClip()).isSameAs(f.after));
        }
    }

    @Test
    void explicitRetryReleasesTheOldThumbnailBeforeMetadataPublication(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            var old = mock(LazyImage.class); var next = mock(LazyImage.class);
            when(old.completion()).thenReturn(new CompletableFuture<>());
            when(next.completion()).thenReturn(new CompletableFuture<>());
            when(f.before.thumbnailUrl()).thenReturn("https://images.invalid/old");
            when(f.after.thumbnailUrl()).thenReturn("https://images.invalid/new");
            when(f.images.load("https://images.invalid/old", 128, 128)).thenReturn(old);
            when(f.images.load("https://images.invalid/new", 128, 128)).thenReturn(next);
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before);
                assertThat(f.item.getThumbnailImage()).isSameAs(old);
                f.item.clipProperty().addListener((observable, before, after) -> {
                    verify(old).close(); assertThat(f.item.getThumbnailImage()).isSameAs(next);
                });
                f.result = f.item.retry();
            });
            f.runNext(); f.result.get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(f.item.getThumbnailImage()).isSameAs(next));
        }
    }

    @Test
    void theItemMapCannotBeMutatedByItsConsumers(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before);
                assertThatThrownBy(() -> f.repository.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThat(f.repository.getItems()).containsEntry("clip", f.item);
            });
        }
    }

    @Test
    void firstUnknownPostStartsOneLookupAndAnExplicitRetrySharesIt(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.item = f.source.post("clip", null);
                f.source.post("clip", null);
                assertThat(f.queue).hasSize(1);
                f.result = f.item.retry();
                assertThat(f.queue).hasSize(1);
                assertThat(f.item.getTimes()).isEqualTo(2);
            });
            f.runNext(); f.result.get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(f.item.getClip()).isSameAs(f.after));
        }
    }

    @Test
    void aKnownClipEventSupersedesThePendingLookup(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before); f.result = f.item.retry();
                var known = mock(VideoClip.class);
                assertThat(f.source.post("clip", known)).isSameAs(f.item);
                assertThat(f.result.isCancelled()).isTrue();
                assertThat(f.item.getClip()).isSameAs(known);
                assertThat(f.item.getTimes()).isEqualTo(2);
            });
            f.runNext(); assertThat(f.calls).isEmpty();
        }
    }

    @Test
    void aPreviousClientRoomCannotPostIntoTheNewSession(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                var old = f.source;
                var next = mock(Twitch.class, RETURNS_DEEP_STUBS);
                f.repository.setSession(next);
                f.source = new ClipEventSource(f.repository, next, f.owner);
                f.source.emit(old.room(), "old", f.before);
                assertThat(f.repository.getItems()).isEmpty();
                assertThat(f.source.post("new", f.after).getClip()).isSameAs(f.after);
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aLateResponseAfterRemovalCannotUpdateAReplacementOrFinishPhysicalDrainEarly(boolean lateFailure, FxRobot robot) throws Exception {
        try (var f = new Fixture(robot); var worker = Executors.newSingleThreadExecutor()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            f.lookup = (client, url) -> {
                entered.countDown();
                boolean interrupted = false;
                for (;;) {
                    try { release.await(); break; }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
                if (lateFailure) throw new IllegalStateException("late API failure");
                return f.after;
            };
            robot.interact(() -> { f.item = f.source.post("clip", f.before); f.result = f.item.retry(); });
            var actual = worker.submit(f::runNext);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    f.item.remove();
                    assertThat(f.source.post("clip", f.before)).isNotSameAs(f.item);
                    assertThat(f.result.isCancelled()).isTrue();
                    assertThat(f.drains.getFirst().toCompletableFuture().isDone()).isFalse();
                });
            } finally { release.countDown(); }
            actual.get(5, TimeUnit.SECONDS);
            f.drains.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(f.item.getClip()).isSameAs(f.before);
                assertThat(f.repository.getItems().get("clip").getClip()).isSameAs(f.before);
                assertThat(f.errors).isEmpty();
            });
        }
    }

    @Test
    void anExplicitFailurePreservesTheOldClipReportsItsCauseAndAllowsRetry(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            var failure = new IllegalStateException("API unavailable");
            f.lookup = (client, url) -> { throw failure; };
            robot.interact(() -> { f.item = f.source.post("clip", f.before); f.result = f.item.retry(); });
            f.runNext();
            assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> {
                assertThat(f.errors).containsExactly(failure);
                assertThat(f.item.getClip()).isSameAs(f.before);
                f.lookup = (client, url) -> f.after;
                f.result = f.item.retry();
            });
            f.runNext(); f.result.get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(f.item.getClip()).isSameAs(f.after));
        }
    }

    @Test
    void aFailedAutomaticLookupDoesNotOpenAPopupOrRetryOnEveryRepeatedPost(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            f.lookup = (client, url) -> { throw new IllegalStateException("API unavailable"); };
            robot.interact(() -> f.item = f.source.post("clip", null));
            f.runNext();
            robot.interact(() -> {
                assertThat(f.errors).isEmpty();
                f.source.post("clip", null);
                assertThat(f.queue).isEmpty();
                assertThat(f.item.getClip()).isNull();
                assertThat(f.item.getTimes()).isEqualTo(2);
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void executorStartupFailureFinishesTheRequestAndPhysicalDrain(boolean fatal, FxRobot robot) throws Exception {
        Throwable failure = fatal ? new AssertionError("executor unavailable") : new RejectedExecutionException("executor closed");
        Executor rejecting = task -> {
            if (failure instanceof Error error) throw error;
            throw (RuntimeException) failure;
        };
        try (var f = new Fixture(robot, rejecting)) {
            robot.interact(() -> { f.item = f.source.post("clip", f.before); f.result = f.item.retry(); });
            if (fatal) assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasRootCause(failure);
            else assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasCause(failure);
            f.drains.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat(f.calls).isEmpty();
            robot.interact(() -> {
                assertThat(f.errors).containsExactly(failure);
                assertThat(f.item.getClip()).isSameAs(f.before);
            });
        }
    }

    @Test
    void anExplicitRetryOfTheAutomaticRequestReportsItsFailureExactlyOnce(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            var failure = new IllegalStateException("API unavailable");
            f.lookup = (client, url) -> { throw failure; };
            robot.interact(() -> {
                f.item = f.source.post("clip", null);
                f.result = f.item.retry(); assertThat(f.item.retry()).isSameAs(f.result);
            });
            f.runNext();
            assertThatThrownBy(() -> f.result.get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> assertThat(f.errors).containsExactly(failure));
        }
    }

    @Test
    void aNullLookupPreservesPreviouslyKnownMetadata(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            f.lookup = (client, url) -> null;
            robot.interact(() -> { f.item = f.source.post("clip", f.before); f.result = f.item.retry(); });
            f.runNext(); assertThat(f.result.get(5, TimeUnit.SECONDS)).isNull();
            robot.interact(() -> assertThat(f.item.getClip()).isSameAs(f.before));
        }
    }

    @Test
    void closingFromImageReleaseCannotPublishMetadataOrInterruptTheFxThread(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            var thumbnail = mock(LazyImage.class);
            when(thumbnail.completion()).thenReturn(new CompletableFuture<>());
            when(f.before.thumbnailUrl()).thenReturn("https://images.invalid/old");
            when(f.images.load("https://images.invalid/old", 128, 128)).thenReturn(thumbnail);
            doAnswer(ignored -> { f.repository.close(); return null; }).when(thumbnail).close();
            robot.interact(() -> {
                f.item = f.source.post("clip", f.before);
                assertThat(f.item.getThumbnailImage()).isSameAs(thumbnail);
                f.result = f.item.retry();
            });
            f.runNext();
            robot.interact(() -> {
                assertThat(f.repository.isClosed()).isTrue();
                assertThat(f.result.isCancelled()).isTrue();
                assertThat(f.item.getClip()).isSameAs(f.before);
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
            });
        }
    }

    private static final class Fixture implements AutoCloseable {
        final FxRobot robot;
        final ImageLoader images = mock(ImageLoader.class);
        final Twitch twitch = mock(Twitch.class, RETURNS_DEEP_STUBS);
        final Broadcaster owner = new Broadcaster("123", "user", "User", "https://images.invalid/user", "");
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final List<Twitch> calls = new CopyOnWriteArrayList<>();
        final List<String> urls = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<CompletionStage<Void>> drains = new CopyOnWriteArrayList<>();
        final VideoClip before = mock(VideoClip.class), after = mock(VideoClip.class);
        final PostedClipRepository repository;
        ClipEventSource source;
        PostedClipItem item;
        FXTask<?> result;
        BiFunction<Twitch, String, VideoClip> lookup;
        Fixture(FxRobot robot) { this(robot, null); }
        Fixture(FxRobot robot, Executor executor) {
            this.robot = robot;
            lookup = (client, url) -> { calls.add(client); urls.add(url); return after; };
            repository = new PostedClipRepository(images, executor == null ? queue::add : executor, (client, url) -> lookup.apply(client, url), drains::add, errors::add);
            robot.interact(() -> {
                repository.setSession(twitch);
                source = new ClipEventSource(repository, twitch, owner);
            });
        }
        void runNext() { Objects.requireNonNull(queue.poll(), "expected queued lookup").run(); }
        @Override public void close() {
            robot.interact(repository::close);
            Runnable task; while ((task = queue.poll()) != null) task.run();
        }
    }
}
