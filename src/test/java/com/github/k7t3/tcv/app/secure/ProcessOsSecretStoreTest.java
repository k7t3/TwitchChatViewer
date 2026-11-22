package com.github.k7t3.tcv.app.secure;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardWatchEventKinds;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessOsSecretStoreTest {
    @TempDir Path directory;

    @Test
    void unrelatedCredentialsAndJvmOptionsAreNotInheritedByTheHelper() throws Exception {
        var store = new ProcessOsSecretStore(() -> {
            var command = helper("environment");
            command.environment().put("TCV_TEST_UNRELATED_SECRET", "synthetic-fixture");
            command.environment().put("JAVA_TOOL_OPTIONS", "-Dtcv.test.injected.option=synthetic-fixture");
            return command;
        }, Duration.ofSeconds(5));

        assertThat(store.read(UUID.randomUUID().toString())).isNull();

        assertHelperExited("environment");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anOsRejectionReportsItsStatusWithoutRetryingOrPublishingASecret(boolean write) throws Exception {
        var store = new ProcessOsSecretStore(() -> helper("denied"), Duration.ofSeconds(5));
        var id = UUID.randomUUID().toString();

        assertThatThrownBy(() -> {
            if (write) store.save(id, new byte[32]);
            else store.read(id);
        }).isInstanceOf(KeyManagerException.class).hasMessageContaining("-25293");

        assertHelperExited("denied");
        assertThat(directory.resolve(id)).doesNotExist();
    }

    @Test
    void aTimedOutHelperIsActuallyTerminatedBeforeTheCallReturns() throws Exception {
        var store = new ProcessOsSecretStore(() -> helper("hang"), Duration.ofSeconds(2));

        assertThatThrownBy(() -> store.read(UUID.randomUUID().toString()))
                .isInstanceOf(KeyManagerException.class).hasMessageContaining("timed out");

        assertHelperExited("hang");
    }

    @Test
    void interruptingTheCallerPreservesItsInterruptAndWaitsForHelperExit() throws Exception {
        var store = new ProcessOsSecretStore(() -> helper("hang"), Duration.ofSeconds(30));
        var failure = new AtomicReference<Throwable>();
        var interrupted = new AtomicBoolean();
        var completed = new CountDownLatch(1);
        try (var events = directory.getFileSystem().newWatchService()) {
            directory.register(events, StandardWatchEventKinds.ENTRY_CREATE);
            var caller = Thread.ofVirtual().start(() -> {
                try { store.read(UUID.randomUUID().toString()); }
                catch (Throwable caught) { failure.set(caught); }
                finally { interrupted.set(Thread.currentThread().isInterrupted()); completed.countDown(); }
            });
            try {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                var started = false;
                while (!started) {
                    var key = events.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    assertThat(key).as("complete helper PID publication").isNotNull();
                    started = key.pollEvents().stream().anyMatch(event -> event.context().toString().equals("hang.pid"));
                    key.reset();
                }
                assertThat(directory.resolve("hang.pid")).exists();

                caller.interrupt();

                assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(failure.get()).isInstanceOf(KeyManagerException.class).hasCauseInstanceOf(InterruptedException.class);
                assertThat(interrupted).isTrue();
                assertHelperExited("hang");
            } finally {
                caller.interrupt();
                assertThat(caller.join(Duration.ofSeconds(5))).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "extra", "wrong-status", "exit"})
    void aMalformedOrFailedHelperCannotReturnAPartialSecret(String mode) throws Exception {
        var store = new ProcessOsSecretStore(() -> helper(mode), Duration.ofSeconds(5));

        assertThatThrownBy(() -> store.read(UUID.randomUUID().toString())).isInstanceOf(KeyManagerException.class);

        assertHelperExited(mode);
    }

    @Test
    void separateHelperProcessesPersistAndReadTheSameKeyProtectionSecret() throws Exception {
        var store = new ProcessOsSecretStore(() -> helper("normal"), Duration.ofSeconds(5));
        var id = UUID.randomUUID().toString();
        var secret = new byte[32]; Arrays.fill(secret, (byte) 7);

        store.save(id, secret);

        assertThat(store.read(id)).containsExactly(secret);
        assertThat(store.read(UUID.randomUUID().toString())).isNull();
        var path = directory.resolve("protected.ks");
        try (var writer = new KeyManagerImpl(path, store)) {
            writer.store("credential", new SecretKeySpec(secret, "AES"));
        }
        try (var reader = new KeyManagerImpl(path, store)) {
            assertThat(reader.getSecret("credential").getEncoded()).containsExactly(secret);
        }
    }

    private void assertHelperExited(String mode) throws Exception {
        var pid = Long.parseLong(Files.readString(directory.resolve(mode + ".pid")));
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    private ProcessBuilder helper(String mode) {
        try {
            var main = Path.of(KeyManagerImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var tests = Path.of(SecretStoreProcessProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", main + java.io.File.pathSeparator + tests, SecretStoreProcessProbe.class.getName(), directory.toString(), mode);
        } catch (java.net.URISyntaxException failure) { throw new AssertionError(failure); }
    }
}
