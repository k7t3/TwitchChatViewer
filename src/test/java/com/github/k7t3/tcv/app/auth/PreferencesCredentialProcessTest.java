package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferencesCredentialProcessTest {
    @TempDir Path directory;

    @Test
    void anotherProcessCannotAcceptTheSameRevisionWhileAWriterHasNotFlushed() throws Exception {
        var file = directory.resolve("synthetic-preferences.properties");
        var lock = directory.resolve("credentials.lock");
        try (var watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            try (var first = start("paused", file, lock)) {
                awaitFile(directory.resolve("paused.ready"), watcher);
                try (var second = start("independent", file, lock)) {
                    awaitFile(directory.resolve("independent.ready"), watcher);

                    assertThat(second.process.waitFor(500, TimeUnit.MILLISECONDS))
                            .as("the second process must wait for the first comparison, encoding and flush").isFalse();
                    first.process.getOutputStream().write('x');
                    first.process.getOutputStream().flush();

                    first.awaitSuccess();
                    second.awaitSuccess();
                    assertThat(Files.readString(first.log)).contains("accepted=true");
                    assertThat(Files.readString(second.log)).contains("accepted=false");
                    var stored = new PreferencesCredentialStorage(new FilePreferences(file), byte[]::clone, byte[]::clone);
                    assertThat(stored.loadCredentials()).extracting(c -> ((OAuth2Credential) c).getAccessToken())
                            .containsExactly("synthetic-paused");
                }
            }
        }
    }

    @Test
    void permissionIsCheckedAgainAfterWaitingForAnIndependentProcessLock() throws Exception {
        verifyWaitingOperation(false);
    }

    @Test
    void interruptingAFileLockWaitCannotModifyStoredCredentials() throws Exception {
        verifyWaitingOperation(true);
    }

    private void verifyWaitingOperation(boolean interrupt) throws Exception {
        var file = directory.resolve("synthetic-preferences.properties");
        var lock = directory.resolve("credentials.lock");
        try (var watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            try (var holder = start("holder", file, lock)) {
                awaitFile(directory.resolve("holder.ready"), watcher);
                var store = new PreferencesCredentialStorage(new FilePreferences(file), byte[]::clone, byte[]::clone, lock);
                var entered = new java.util.concurrent.CountDownLatch(1);
                var permitted = new java.util.concurrent.atomic.AtomicBoolean(true);
                var interruptRetained = new java.util.concurrent.atomic.AtomicBoolean();
                var result = new java.util.concurrent.CompletableFuture<java.util.Optional<CredentialStore.Snapshot>>();
                var worker = Thread.ofVirtual().start(() -> {
                    try {
                        entered.countDown();
                        result.complete(store.compareAndSet(new CredentialStore.Snapshot("", List.of()), List.of(), permitted::get));
                    } catch (Throwable failure) { result.completeExceptionally(failure); }
                    finally { interruptRetained.set(Thread.currentThread().isInterrupted()); }
                });
                Thread interrupter = null;
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                    if (interrupt) {
                        var cancellationReturned = new java.util.concurrent.CompletableFuture<Void>();
                        interrupter = Thread.ofVirtual().start(() -> {
                            worker.interrupt();cancellationReturned.complete(null);
                        });
                        cancellationReturned.get(2, TimeUnit.SECONDS);
                        assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                                .isInstanceOf(java.util.concurrent.ExecutionException.class)
                                .hasCauseInstanceOf(java.io.UncheckedIOException.class)
                                .satisfies(failure -> assertThat(failure.getCause())
                                        .hasCauseInstanceOf(java.nio.channels.FileLockInterruptionException.class));
                    } else permitted.set(false);
                } finally {
                    holder.process.getOutputStream().write('x');holder.process.getOutputStream().flush();
                    holder.awaitSuccess();
                    worker.join(java.time.Duration.ofSeconds(5));
                    if (worker.isAlive()) { worker.interrupt();worker.join(java.time.Duration.ofSeconds(5)); }
                    assertThat(worker.isAlive()).isFalse();
                    if (interrupter != null) {
                        interrupter.join(java.time.Duration.ofSeconds(5));
                        assertThat(interrupter.isAlive()).isFalse();
                    }
                }
                if (interrupt) assertThat(interruptRetained).isTrue();
                else assertThat(result.get(5, TimeUnit.SECONDS)).isEmpty();
                assertThat(file).doesNotExist();
                assertThat(store.loadCredentials()).isEmpty();
            }
        }
    }

    private Child start(String mode, Path file, Path lock) throws IOException {
        var log = directory.resolve(mode + ".log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Writer.class.getName(), mode,
                file.toString(), lock.toString(), directory.resolve(mode + ".ready").toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        return new Child(process, log);
    }

    private static void awaitFile(Path file, WatchService watcher) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(file)) {
            var remaining = deadline - System.nanoTime();
            assertThat(remaining).as("child must reach the controlled boundary").isPositive();
            var event = watcher.poll(remaining, TimeUnit.NANOSECONDS);
            assertThat(event).isNotNull();
            event.pollEvents();event.reset();
        }
    }

    private record Child(Process process, Path log) implements AutoCloseable {
        void awaitSuccess() throws Exception {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
        }
        @Override public void close() throws Exception {
            if (process.isAlive()) process.destroyForcibly();
            try { assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue(); }
            finally {
                process.getOutputStream().close();process.getInputStream().close();process.getErrorStream().close();
            }
        }
    }

    public static final class Writer {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("holder")) {
                try (var channel = FileChannel.open(Path.of(args[2]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     var held = channel.lock()) {
                    Files.createFile(Path.of(args[3]));
                    if (System.in.read() != 'x') throw new AssertionError("lock holder was not resumed");
                }
                return;
            }
            var paused = args[0].equals("paused");
            var node = new FilePreferences(Path.of(args[1]));
            var ready = Path.of(args[3]);
            UnaryOperator<byte[]> encrypt = bytes -> {
                if (paused) {
                    try {
                        Files.createFile(ready);
                        if (System.in.read() != 'x') throw new AssertionError("writer was not resumed");
                    } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }
                return bytes;
            };
            var store = new PreferencesCredentialStorage(node, encrypt, byte[]::clone, Path.of(args[2]));
            var expected = new CredentialStore.Snapshot("", List.of());
            if (!paused) Files.createFile(ready);
            var credential = new OAuth2Credential("synthetic", "synthetic-" + args[0], null,
                    args[0], args[0], 3600, List.of(), Map.of());
            credential.setReceivedAt(Instant.EPOCH);
            var accepted = store.compareAndSet(expected, List.of(credential), () -> true).isPresent();
            node.flush();
            System.out.println("accepted=" + accepted);
        }
    }

    static class FilePreferences extends AbstractPreferences {
        private final Path file;
        private final Properties values = new Properties();
        FilePreferences(Path file) throws BackingStoreException { super(null, "");this.file = file;syncSpi(); }
        @Override public boolean isUserNode() { return false; }
        @Override protected void putSpi(String key, String value) { values.setProperty(key, value); }
        @Override protected String getSpi(String key) { return values.getProperty(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { throw new UnsupportedOperationException(); }
        @Override protected String[] keysSpi() { return values.stringPropertyNames().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() throws BackingStoreException {
            values.clear();
            try {
                if (Files.exists(file)) try (var input = Files.newInputStream(file)) { values.load(input); }
            } catch (IOException failure) { throw new BackingStoreException(failure); }
        }
        @Override protected void flushSpi() throws BackingStoreException {
            try {
                var bytes = new ByteArrayOutputStream();values.store(bytes, "synthetic credential fixture");
                try (var output = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    var buffer = ByteBuffer.wrap(bytes.toByteArray());
                    while (buffer.hasRemaining()) output.write(buffer);
                    output.force(true);
                }
            } catch (IOException failure) { throw new BackingStoreException(failure); }
        }
    }
}
