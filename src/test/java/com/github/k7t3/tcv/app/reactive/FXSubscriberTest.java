package com.github.k7t3.tcv.app.reactive;

import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(ApplicationExtension.class)
class FXSubscriberTest {

    @Test
    void cancellationDiscardsAlreadyQueuedChatMessage(FxRobot robot) {
        var items = new ArrayList<ChatMessageEvent>();
        var subscriber = new ChatMessageSubscriber(items::add);
        var message = new ChatMessageEvent(null, null);
        robot.interact(() -> {
            subscriber.onSubscribe(mock(Flow.Subscription.class));
            enqueueFromWorker(() -> subscriber.onNext(message));
            subscriber.cancel();
        });

        robot.interact(() -> assertThat(items).isEmpty());
    }

    @Test
    void cancellationDiscardsAlreadyQueuedFxNotification(FxRobot robot) {
        var items = new ArrayList<String>();
        var subscriber = new DownCastFXSubscriber<Object, String>(String.class, items::add);
        robot.interact(() -> {
            subscriber.onSubscribe(mock(Flow.Subscription.class));
            enqueueFromWorker(() -> subscriber.onNext("queued"));
            subscriber.cancel();
        });

        robot.interact(() -> assertThat(items).isEmpty());
    }

    private static void enqueueFromWorker(Runnable action) {
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            worker.submit(action).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
