package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelLoadingCancellationTest {
    private static final Broadcaster OWNER = new Broadcaster("123", "synthetic", "Synthetic", "profile", "");

    @Test
    void cancellingAPublicLoadOffFxPreventsItsQueuedFetchBeforeFxDelivery(FxRobot robot) {
        var domain = mock(ChannelRepository.class);
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class),
                queue::add, ignored -> {}, failure -> { throw new AssertionError(failure); });
        try {
            robot.interact(() -> {
                model.setRepository(domain);
                var result = model.getChannelAsync(OWNER);

                background(() -> { result.cancel(true);queue.remove().run(); });

                assertThat(result.isCancelled()).isTrue();
                verify(domain, never()).getOrLoadChannel(OWNER);
                assertThat(model.getChannels()).isEmpty();
            });
        } finally {
            robot.interact(model::close);
            Runnable task;while ((task = queue.poll()) != null) task.run();
        }
    }

    @Test
    void cancellationByTheDomainTerminatesThePublicLoadBeforeFxDelivery(FxRobot robot) {
        var domain = mock(ChannelRepository.class);
        when(domain.getOrLoadChannel(OWNER)).thenThrow(new CancellationException("synthetic session cancellation"));
        var queue = new LinkedBlockingQueue<Runnable>();
        var failures = new ArrayList<Throwable>();
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class),
                queue::add, ignored -> {}, failures::add);
        try {
            robot.interact(() -> {
                model.setRepository(domain);
                var result = model.getChannelAsync(OWNER);

                background(() -> queue.remove().run());

                assertThat(result.isCancelled()).isTrue();
                assertThat(result.completion().toCompletableFuture()).isCompletedExceptionally();
                assertThat(model.getChannels()).isEmpty();
                assertThat(failures).isEmpty();
            });
        } finally {
            robot.interact(model::close);
            Runnable task;while ((task = queue.poll()) != null) task.run();
        }
    }

    private static void background(Runnable operation) {
        var finished = new CompletableFuture<Void>();
        var worker = Thread.startVirtualThread(() -> {
            try { operation.run();finished.complete(null); }
            catch (Throwable failure) { finished.completeExceptionally(failure); }
        });
        assertThatCode(() -> finished.get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
        assertThatCode(() -> worker.join(Duration.ofSeconds(5))).doesNotThrowAnyException();
        assertThat(worker.isAlive()).isFalse();
    }
}
