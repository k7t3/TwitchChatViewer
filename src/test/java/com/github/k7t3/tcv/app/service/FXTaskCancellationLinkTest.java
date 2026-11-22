package com.github.k7t3.tcv.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(ApplicationExtension.class)
class FXTaskCancellationLinkTest {
    @Test
    void linkingAnOwnedWorkerAlsoInterruptsItsRunningCallBeforeFxDelivery(FxRobot robot) {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CompletableFuture<Void>();
        var interrupted = new AtomicBoolean();
        var execution = new AtomicReference<Thread>();
        var source = FXTask.observation(() -> null);
        var worker = FXTask.task(() -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException failure) { interrupted.set(true);Thread.currentThread().interrupt(); }
            finally { exited.complete(null); }
            return null;
        });
        try {
            robot.interact(() -> {
                worker.cancelWhenCancelled(source);
                execution.set(Thread.startVirtualThread(worker::run));
                assertThatCode(() -> assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()).doesNotThrowAnyException();

                source.cancel(false);

                assertThatCode(() -> exited.get(2, TimeUnit.SECONDS)).doesNotThrowAnyException();
                assertThat(worker.isCancelled()).isTrue();
                assertThat(interrupted).isTrue();
            });
        } finally {
            release.countDown();
            if (execution.get() != null) {
                assertThatCode(() -> execution.get().join(Duration.ofSeconds(5))).doesNotThrowAnyException();
                assertThat(execution.get().isAlive()).isFalse();
            }
        }
    }
}
