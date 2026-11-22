package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyManagerCancellationTest {
    @TempDir Path directory;
    private final char[] password = "synthetic-key-password".toCharArray();

    @Test
    void cancellingAKeyReadWaitingForAnotherProcessDoesNotWaitForTheLockOwner() throws Exception {
        var path = directory.resolve("synthetic-keys.p12");
        var original = new SecretKeySpec(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, "AES");
        try (var manager = new KeyManagerImpl(path, password)) {
            manager.store("original", original);
            var before = Files.readAllBytes(path);
            try (var holder = hold(path)) {
                var entered = new CountDownLatch(1);
                var interruptRetained = new AtomicBoolean();
                var result = new CompletableFuture<SecretKey>();
                var worker = Thread.startVirtualThread(() -> {
                    try { entered.countDown();result.complete(manager.getSecret("original")); }
                    catch (Throwable failure) { result.completeExceptionally(failure); }
                    finally { interruptRetained.set(Thread.currentThread().isInterrupted()); }
                });
                Thread cancelling = null;
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    var cancellationReturned = new CompletableFuture<Void>();

                    cancelling = Thread.startVirtualThread(() -> { worker.interrupt();cancellationReturned.complete(null); });

                    cancellationReturned.get(2, TimeUnit.SECONDS);
                    assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                            .isInstanceOf(java.util.concurrent.ExecutionException.class)
                            .hasCauseInstanceOf(KeyManagerException.class)
                            .satisfies(failure -> assertThat(failure.getCause()).hasCauseInstanceOf(IOException.class));
                } finally {
                    holder.release();
                    worker.join(Duration.ofSeconds(5));
                    assertThat(worker.isAlive()).isFalse();
                    if (cancelling != null) {
                        cancelling.join(Duration.ofSeconds(5));
                        assertThat(cancelling.isAlive()).isFalse();
                    }
                }
                assertThat(interruptRetained).isTrue();
            }
            assertThat(Files.readAllBytes(path)).containsExactly(before);
            assertThat(manager.getSecret("original")).isEqualTo(original);
            try (var independent = new KeyManagerImpl(path, password)) {
                assertThat(independent.getSecret("original")).isEqualTo(original);
                assertThat(independent.getSecret("unexpected")).isNull();
            }
        }
    }

    @Test
    void waitingForAnotherManagerInTheSameJvmCanBeCancelledBeforeItsWriterFinishes() throws Exception {
        verifyJvmWait(false);
    }

    @Test
    void waitingForTheSameManagerCanBeCancelledBeforeItsAcceptedWriterFinishes() throws Exception {
        verifyJvmWait(true);
    }

    private void verifyJvmWait(boolean sameManager) throws Exception {
        var path = directory.resolve("synthetic-keys.p12");
        var original = new SecretKeySpec(new byte[16], "AES");
        var nextBytes = new byte[16];java.util.Arrays.fill(nextBytes, (byte) 2);
        var next = new SecretKeySpec(nextBytes, "AES");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var first = new KeyManagerImpl(path, password);var second = sameManager ? first : new KeyManagerImpl(path, password);
             var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            first.store("original", original);
            var saving = workers.submit(() -> first.getOrCreateSecret("new", () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("key generator not released");
                } catch (InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
                return next;
            }));
            Thread reader = null;
            var interrupted = new AtomicBoolean();
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var attempting = new CountDownLatch(1);
                var result = new CompletableFuture<SecretKey>();
                reader = Thread.startVirtualThread(() -> {
                    try { attempting.countDown();result.complete(second.getSecret("original")); }
                    catch (Throwable failure) { result.completeExceptionally(failure); }
                    finally { interrupted.set(Thread.currentThread().isInterrupted()); }
                });
                assertThat(attempting.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                reader.interrupt();

                assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(KeyManagerException.class)
                        .satisfies(failure -> assertThat(failure.getCause()).hasCauseInstanceOf(java.io.InterruptedIOException.class));
            } finally {
                release.countDown();
                if (reader != null) { reader.join(Duration.ofSeconds(5));assertThat(reader.isAlive()).isFalse(); }
            }
            assertThat(interrupted).isTrue();
            assertThat(saving.get(5, TimeUnit.SECONDS)).isEqualTo(next);
            assertThat(first.getSecret("original")).isEqualTo(original);
            assertThat(first.getSecret("new")).isEqualTo(next);
        }
        try (var independent = new KeyManagerImpl(path, password)) {
            assertThat(independent.getSecret("original")).isEqualTo(original);
            assertThat(independent.getSecret("new")).isEqualTo(next);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "os-protected"})
    void constructionCanBeCancelledBeforeItReadsAnyOsSecret(String mode) throws Exception {
        var path = directory.resolve("synthetic-keys.p12");
        try (var seed = new KeyManagerImpl(path, password)) { seed.store("original", new SecretKeySpec(new byte[16], "AES")); }
        var before = Files.readAllBytes(path);
        var secrets = org.mockito.Mockito.mock(OsSecretStore.class);
        var result = new CompletableFuture<Void>();
        var entered = new CountDownLatch(1);
        var interruptRetained = new AtomicBoolean();
        try (var holder = hold(path)) {
            var worker = Thread.startVirtualThread(() -> {
                try {
                    entered.countDown();
                    try (var ignored = mode.equals("password") ? new KeyManagerImpl(path, password) : new KeyManagerImpl(path, secrets)) {
                        result.complete(null);
                    }
                } catch (Throwable failure) { result.completeExceptionally(failure); }
                finally { interruptRetained.set(Thread.currentThread().isInterrupted()); }
            });
            Thread cancelling = null;
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                var returned = new CompletableFuture<Void>();
                cancelling = Thread.startVirtualThread(() -> { worker.interrupt();returned.complete(null); });

                returned.get(2, TimeUnit.SECONDS);
                assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(KeyManagerException.class);
            } finally {
                holder.release();
                worker.join(Duration.ofSeconds(5));assertThat(worker.isAlive()).isFalse();
                if (cancelling != null) { cancelling.join(Duration.ofSeconds(5));assertThat(cancelling.isAlive()).isFalse(); }
            }
            assertThat(interruptRetained).isTrue();
        }
        org.mockito.Mockito.verifyNoInteractions(secrets);
        assertThat(password).containsExactly("synthetic-key-password".toCharArray());
        assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test
    void anInterruptedCloseStillWaitsForAcceptedPublicationAndPreservesTheInterrupt() throws Exception {
        var path = directory.resolve("synthetic-keys.p12");
        var writer = new KeyManagerImpl(path, password);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var key = new SecretKeySpec(new byte[16], "AES");
        var closed = new CompletableFuture<Void>();
        var interruptRetained = new AtomicBoolean();
        Thread closer = null;
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var saved = workers.submit(() -> writer.getOrCreateSecret("accepted", () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("key generator not released");
                } catch (InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
                return key;
            }));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                closer = Thread.startVirtualThread(() -> {
                    Thread.currentThread().interrupt();
                    try { writer.close();interruptRetained.set(Thread.currentThread().isInterrupted());closed.complete(null); }
                    catch (Throwable failure) { closed.completeExceptionally(failure); }
                });

                assertThatThrownBy(() -> closed.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            assertThat(saved.get(5, TimeUnit.SECONDS)).isEqualTo(key);
            closed.get(5, TimeUnit.SECONDS);
            assertThat(interruptRetained).isTrue();
            assertThatThrownBy(() -> writer.getSecret("accepted")).isInstanceOf(IllegalStateException.class);
            try (var independent = new KeyManagerImpl(path, password)) {
                assertThat(independent.getSecret("accepted")).isEqualTo(key);
            }
        } finally {
            release.countDown();writer.close();
            if (closer != null) { closer.join(Duration.ofSeconds(5));assertThat(closer.isAlive()).isFalse(); }
        }
    }

    private HeldLock hold(Path path) throws Exception {
        var ready = directory.resolve("lock.ready");
        var log = directory.resolve("lock-holder.log");
        try (var watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), LockHolder.class.getName(),
                    path.resolveSibling(path.getFileName() + ".lock").toString(), ready.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            var held = new HeldLock(process, log);
            try {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(ready)) {
                    var remaining = deadline - System.nanoTime();
                    assertThat(remaining).isPositive();
                    var event = watcher.poll(remaining, TimeUnit.NANOSECONDS);
                    assertThat(event).isNotNull();event.pollEvents();event.reset();
                }
                return held;
            } catch (Exception | Error failure) {
                try { held.close(); } catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
    }

    private record HeldLock(Process process, Path log) implements AutoCloseable {
        void release() throws Exception {
            if (process.isAlive()) { process.getOutputStream().write('x');process.getOutputStream().flush(); }
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
        }
        @Override public void close() throws Exception {
            if (process.isAlive()) process.destroyForcibly();
            try { assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue(); }
            finally { process.getOutputStream().close();process.getInputStream().close();process.getErrorStream().close(); }
        }
    }

    public static final class LockHolder {
        public static void main(String[] args) throws Exception {
            try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                Files.createFile(Path.of(args[1]));
                if (System.in.read() != 'x') throw new AssertionError("lock holder not released");
            }
        }
    }
}
