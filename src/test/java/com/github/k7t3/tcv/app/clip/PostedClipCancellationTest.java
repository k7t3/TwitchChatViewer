package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import com.github.k7t3.tcv.support.ClipEventSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class PostedClipCancellationTest {
    @Test
    void cancellationByTheApiTerminatesThePublicRetryWithoutWaitingForFxDelivery(FxRobot robot) {
        var twitch = mock(Twitch.class);
        when(twitch.getChat()).thenReturn(mock(com.github.twitch4j.chat.TwitchChat.class));
        var api = mock(TwitchAPI.class);
        when(twitch.getTwitchAPI()).thenReturn(api);
        when(api.getClips(anyList())).thenThrow(new CancellationException("synthetic session cancellation"));
        var queue = new LinkedBlockingQueue<Runnable>();
        var failures = new ArrayList<Throwable>();
        var drained = new ArrayList<CompletionStage<Void>>();
        var repository = new PostedClipRepository(mock(ImageLoader.class), queue::add, drained::add, failures::add);
        var owner = new Broadcaster("123", "synthetic", "Synthetic", "https://images.invalid/user", "");
        var original = mock(VideoClip.class);
        var item = new AtomicReference<PostedClipItem>();
        var result = new AtomicReference<FXTask<?>>();
        try {
            robot.interact(() -> {
                repository.setSession(twitch);
                item.set(new ClipEventSource(repository, twitch, owner).post("clip", original));
                result.set(item.get().retry());
                var finished = new CompletableFuture<Void>();
                var worker = Thread.startVirtualThread(() -> {
                    try { queue.remove().run();finished.complete(null); }
                    catch (Throwable failure) { finished.completeExceptionally(failure); }
                });
                assertThatCode(() -> finished.get(5, TimeUnit.SECONDS))
                        .doesNotThrowAnyException();
                assertThatCode(() -> worker.join(Duration.ofSeconds(5)))
                        .doesNotThrowAnyException();
                assertThat(worker.isAlive()).isFalse();

                assertThat(result.get().isCancelled()).isTrue();
                assertThat(result.get().completion().toCompletableFuture()).isCompletedExceptionally();
                assertThat(item.get().getClip()).isSameAs(original);
                assertThat(failures).isEmpty();
                assertThat(drained).singleElement().satisfies(stage -> assertThat(stage.toCompletableFuture()).isCompleted());
            });
        } finally {
            robot.interact(repository::close);
            Runnable task;while ((task = queue.poll()) != null) task.run();
        }
    }
}
