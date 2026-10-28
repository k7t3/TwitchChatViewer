package com.github.k7t3.tcv.domain;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.chat.TwitchChat;
import com.github.k7t3.tcv.domain.HelixClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class TwitchSchedulerOwnershipTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"runtime", "error", "cancelled"})
    void partialConstructionWaitsForOwnedWorkAndPreservesItsOriginalFailure(String mode) throws Exception {
        var scheduler = new ScheduledThreadPoolExecutor(1);
        var events = mock(EventManager.class);
        var http = mock(HelixClientFactory.Handle.class);
        var chat = mock(TwitchChat.class);
        var cancelled = new AtomicBoolean();
        Throwable original = mode.equals("error") ? new AssertionError("chat construction failed")
                : new IllegalStateException("chat construction failed");
        var cleanupFailure = new IllegalStateException("HTTP cleanup failed");
        doThrow(cleanupFailure).when(http).close();
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var factory = new TwitchClientFactory(() -> scheduler, () -> events, context -> {
            scheduler.submit(() -> {
                entered.countDown();
                holdUntilReleased(finish, exited, interrupted);
            });
            await(entered);
            return http;
        }, context -> {
            if (mode.equals("cancelled")) {
                cancelled.set(true);
                return chat;
            }
            if (original instanceof Error error) throw error;
            throw (RuntimeException) original;
        });
        try (var opener = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var opening = opener.submit(() -> {
                    try {
                        factory.open(mock(CredentialManager.class), new OAuth2Credential("twitch", "synthetic-token"), cancelled::get);
                        return null;
                    } catch (RuntimeException | Error failure) { return failure; }
                });
                assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> opening.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                assertThat(exited.getCount()).isEqualTo(1);
                finish.countDown();
                var failure = opening.get(5, TimeUnit.SECONDS);
                if (mode.equals("cancelled")) assertThat(failure).isInstanceOf(CancellationException.class);
                else assertThat(failure).isSameAs(original);
                assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
                assertThat(scheduler.isTerminated()).isTrue();
                verify(http).close();
                verify(events).close();
                if (mode.equals("cancelled")) verify(chat).close();
                else verify(chat, never()).close();
            } finally { finish.countDown(); }
        } finally { scheduler.shutdownNow(); scheduler.close(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"module-reentry", "worker-reentry"})
    void closeCallbacksDoNotWaitOnThemselves(String mode) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-javaagent:" + Path.of(org.mockito.Mockito.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(CloseCallbackProbe.class.getName());
        command.add(mode);
        var log = directory.resolve(mode + ".log");
        // 自己待機が再発した場合の停止範囲を、子JVMに限定する。
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).withFailMessage(Files.readString(log)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("close callback completed: " + mode);
        } finally { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
    }

    public static final class CloseCallbackProbe {
        public static void main(String[] args) throws Exception {
            try (var fixture = new Fixture(); var closer = Executors.newVirtualThreadPerTaskExecutor()) {
                if (args[0].equals("module-reentry")) {
                    doAnswer(call -> {
                        fixture.resources.client().close();
                        return null;
                    }).when(fixture.http).close();
                    closer.submit(fixture.resources.client()::close).get(5, TimeUnit.SECONDS);
                } else {
                    var entered = new CountDownLatch(1);
                    var interrupted = new CountDownLatch(1);
                    var allowCallback = new CountDownLatch(1);
                    var worker = fixture.scheduler.submit(() -> {
                        fixture.scheduler.taskThread.set(Thread.currentThread());
                        entered.countDown();
                        holdUntilReleased(allowCallback, new CountDownLatch(1), interrupted);
                        fixture.resources.client().close();
                    });
                    await(entered);
                    var closing = closer.submit(fixture.resources.client()::close);
                    await(interrupted);
                    allowCallback.countDown();
                    worker.get(5, TimeUnit.SECONDS);
                    closing.get(5, TimeUnit.SECONDS);
                }
                fixture.verifyModulesClosedOnce();
                assertThat(fixture.scheduler.isTerminated()).isTrue();
                System.out.println("close callback completed: " + args[0]);
            }
        }
    }

    @Test
    void closingCancelsDeferredSdkWorkWithoutLeavingAnUnsettledFuture() throws Exception {
        try (var fixture = new Fixture()) {
            var calls = new AtomicInteger();
            var pending = fixture.scheduler.schedule(calls::incrementAndGet, 1, TimeUnit.DAYS);
            try {
                fixture.resources.client().close();

                assertThat(pending.isCancelled()).isTrue();
                assertThatThrownBy(() -> pending.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(CancellationException.class);

                assertThat(calls).hasValue(0);
                assertThat(fixture.scheduler.isTerminated()).isTrue();
                fixture.verifyModulesClosedOnce();
            } finally { pending.cancel(false); }
        }
    }

    @ParameterizedTest(name = "concurrent SDK close, primary failure={0}")
    @ValueSource(booleans = {false, true})
    void concurrentCloseWaitsForTheFirstSdkModuleReleaseAttempt(boolean primaryFails) throws Exception {
        var moduleEntered = new CountDownLatch(1);
        var finishModule = new CountDownLatch(1);
        var waiterEntered = new CountDownLatch(1);
        var failure = new IllegalStateException("HTTP module close failed");
        try (var fixture = new Fixture(); var closers = Executors.newVirtualThreadPerTaskExecutor()) {
            doAnswer(call -> {
                moduleEntered.countDown();
                if (!finishModule.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release HTTP close");
                if (primaryFails) throw failure;
                return null;
            }).when(fixture.http).close();
            try {
                var primary = closers.submit(fixture.resources.client()::close);
                assertThat(moduleEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var waiter = closers.submit(() -> {
                    waiterEntered.countDown();
                    fixture.resources.client().close();
                });
                assertThat(waiterEntered.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> waiter.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                assertThat(primary.isDone()).isFalse();
                finishModule.countDown();
                if (primaryFails) assertThatThrownBy(() -> primary.get(5, TimeUnit.SECONDS)).hasCause(failure);
                else primary.get(5, TimeUnit.SECONDS);
                waiter.get(5, TimeUnit.SECONDS);
                fixture.resources.client().close();
                fixture.verifyModulesClosedOnce();
                assertThat(fixture.scheduler.isTerminated()).isTrue();
            } finally { finishModule.countDown(); }
        }
    }

    @Test
    void externalCloseAfterAWorkerStopStillWaitsForThatWorkersActualExit() throws Exception {
        var finish = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var workerClosed = new CompletableFuture<Throwable>();
        var closingEntered = new CountDownLatch(1);
        try (var fixture = new Fixture(); var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var worker = fixture.scheduler.submit(() -> {
                    fixture.scheduler.taskThread.set(Thread.currentThread());
                    try {
                        fixture.resources.client().close();
                        workerClosed.complete(null);
                    } catch (RuntimeException | Error failure) {
                        workerClosed.complete(failure);
                    }
                    holdUntilReleased(finish, exited);
                });
                assertThat(workerClosed.get(5, TimeUnit.SECONDS)).isNull();
                var closing = closer.submit(() -> {
                    closingEntered.countDown();
                    fixture.resources.client().close();
                });
                assertThat(closingEntered.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                assertThat(exited.getCount()).isEqualTo(1);
                finish.countDown();
                worker.get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);
                assertThat(fixture.scheduler.isTerminated()).isTrue();
                fixture.verifyModulesClosedOnce();
            } finally { finish.countDown(); }
        }
    }

    @ParameterizedTest(name = "owned scheduler exit, module failure={0}")
    @ValueSource(booleans = {false, true})
    void closingTheSdkClientWaitsForItsOwnedSchedulerTaskToExit(boolean moduleFails) throws Exception {
        var scheduler = new ScheduledThreadPoolExecutor(1);
        var events = mock(EventManager.class);
        var http = mock(HelixClientFactory.Handle.class);
        when(http.client()).thenReturn(mock(HelixClient.class));
        var chat = mock(TwitchChat.class);
        var factory = new TwitchClientFactory(() -> scheduler, () -> events, context -> http, context -> chat);
        var resources = factory.open(mock(CredentialManager.class), new OAuth2Credential("twitch", "synthetic-token"));
        var moduleFailure = new IllegalStateException("HTTP module close failed");
        if (moduleFails) doThrow(moduleFailure).when(http).close();
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        try (var closer = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                scheduler.submit(() -> {
                    boolean restoreInterrupt = false;
                    entered.countDown();
                    try {
                        while (finish.getCount() != 0) {
                            try {
                                if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release scheduler task");
                            } catch (InterruptedException expected) {
                                restoreInterrupt = true;
                                interrupted.countDown();
                            }
                        }
                    } finally {
                        if (restoreInterrupt) Thread.currentThread().interrupt();
                        exited.countDown();
                    }
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = closer.submit(resources.client()::close);
                assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                assertThat(exited.getCount()).isEqualTo(1);
                finish.countDown();
                if (moduleFails) assertThatThrownBy(() -> closing.get(5, TimeUnit.SECONDS)).hasCause(moduleFailure);
                else closing.get(5, TimeUnit.SECONDS);
                assertThat(exited.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(scheduler.isTerminated()).isTrue();
                resources.client().close();
                verify(http).close();
                verify(chat).close();
                verify(events).close();
            } finally {
                finish.countDown();
                resources.close();
                scheduler.shutdownNow();
            }
        } finally {
            finish.countDown();
            scheduler.shutdownNow();
            scheduler.close();
        }
    }

    private static void holdUntilReleased(CountDownLatch finish, CountDownLatch exited) {
        holdUntilReleased(finish, exited, new CountDownLatch(1));
    }

    private static void holdUntilReleased(CountDownLatch finish, CountDownLatch exited, CountDownLatch interrupted) {
        boolean restoreInterrupt = false;
        try {
            while (finish.getCount() != 0) {
                try {
                    if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release scheduler task");
                } catch (InterruptedException expected) {
                    restoreInterrupt = true;
                    interrupted.countDown();
                }
            }
        } finally {
            if (restoreInterrupt) Thread.currentThread().interrupt();
            exited.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not reach its expected state");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class GuardedScheduler extends ScheduledThreadPoolExecutor {
        final AtomicReference<Thread> taskThread = new AtomicReference<>();
        GuardedScheduler() { super(1); }
        @Override public void close() {
            // 自己待機の回帰を、無限停止ではなくテスト失敗として検出する。
            if (Thread.currentThread() == taskThread.get()) throw new AssertionError("scheduler cannot await itself");
            super.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GuardedScheduler scheduler = new GuardedScheduler();
        final EventManager events = mock(EventManager.class);
        final HelixClientFactory.Handle http = mock(HelixClientFactory.Handle.class);
        final TwitchChat chat = mock(TwitchChat.class);
        final TwitchClientFactory.Resources resources;
        Fixture() {
            when(http.client()).thenReturn(mock(HelixClient.class));
            var factory = new TwitchClientFactory(() -> scheduler, () -> events, context -> http, context -> chat);
            resources = factory.open(mock(CredentialManager.class), new OAuth2Credential("twitch", "synthetic-token"));
        }
        void verifyModulesClosedOnce() {
            verify(http).close();
            verify(chat).close();
            verify(events).close();
        }
        @Override public void close() {
            try { resources.close(); }
            finally { scheduler.shutdownNow(); scheduler.close(); }
        }
    }
}
