package com.github.k7t3.tcv.app.secure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyManagerDurabilityTest {
    @TempDir Path directory;
    private final char[] password = "test-password".toCharArray();

    @Test
    void separateProcessesAlsoShareOneGenerationAndReadCommittedKeys() throws Exception {
        var path = directory.resolve("keys.p12");
        var ready = directory.resolve("ready");
        var firstGenerated = directory.resolve("first-generated");
        var secondGenerated = directory.resolve("second-generated");
        var firstResult = directory.resolve("first-result");
        var secondResult = directory.resolve("second-result");
        var classpath = Path.of(KeyManagerImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator
                + Path.of(KeyStoreProcessProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var first = new ProcessBuilder(java, "-cp", classpath, KeyStoreProcessProbe.class.getName(),
                path.toString(), ready.toString(), firstGenerated.toString(), firstResult.toString(), "1")
                .redirectErrorStream(true).redirectOutput(directory.resolve("first.log").toFile()).start();
        Process second = null;
        try {
            second = new ProcessBuilder(java, "-cp", classpath, KeyStoreProcessProbe.class.getName(),
                    path.toString(), ready.toString(), secondGenerated.toString(), secondResult.toString(), "2")
                    .redirectErrorStream(true).redirectOutput(directory.resolve("second.log").toFile()).start();
            Files.createFile(ready);
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).withFailMessage(Files.readString(directory.resolve("first.log"))).isZero();
            assertThat(second.exitValue()).withFailMessage(Files.readString(directory.resolve("second.log"))).isZero();
            assertThat(Files.readAllBytes(firstResult)).containsExactly(Files.readAllBytes(secondResult));
            assertThat(Files.exists(firstGenerated) ^ Files.exists(secondGenerated)).isTrue();
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(reader.getSecret("credential").getEncoded()).containsExactly(Files.readAllBytes(firstResult));
            }
        } finally {
            first.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            if (second != null) second.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void aCreatedKeyIsDurableBeforeTheWriterIsClosed() {
        var path = directory.resolve("keys.p12");
        try (var writer = new KeyManagerImpl(path, password)) {
            var key = writer.getOrCreateSecret("credential", () -> key(1));
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(reader.getSecret("credential")).isEqualTo(key);
            }
        }
    }

    @Test
    void concurrentManagersGenerateOneDurableKeyAndDoNotLoseOtherAliases() throws Exception {
        var path = directory.resolve("keys.p12");
        var managers = new ArrayList<KeyManagerImpl>();
        for (var index = 0; index < 6; index++) managers.add(new KeyManagerImpl(path, password));
        var generated = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<java.util.concurrent.Future<SecretKey>>();
            for (var manager : managers) results.add(workers.submit(() -> {
                start.await();
                return manager.getOrCreateSecret("credential", () -> key(generated.incrementAndGet()));
            }));
            start.countDown();
            for (var result : results) assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(key(1));
            assertThat(generated).hasValue(1);
            managers.get(0).store("first", key(2));
            managers.get(1).store("second", key(3));
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(reader.getSecret("credential")).isEqualTo(key(1));
                assertThat(reader.getSecret("first")).isEqualTo(key(2));
                assertThat(reader.getSecret("second")).isEqualTo(key(3));
            }
        } finally { managers.forEach(KeyManagerImpl::close); }
    }

    @Test
    void failedReplacementPreservesTheExistingFileAndCanBeRetried() throws Exception {
        var path = directory.resolve("keys.p12");
        try (var initial = new KeyManagerImpl(path, password)) { initial.store("original", key(1)); }
        var before = Files.readAllBytes(path);
        var fail = new AtomicBoolean(true);
        var failure = new IOException("replacement failed");
        var files = new AtomicKeyStoreFile(AtomicKeyStoreFile::writeAndForce, (temporary, target) -> {
            if (fail.get()) throw failure;
            AtomicKeyStoreFile.replace(temporary, target);
        });
        try (var writer = new KeyManagerImpl(path, password, files)) {
            assertThatThrownBy(() -> writer.store("new", key(2))).isInstanceOf(KeyManagerException.class).hasCause(failure);
            assertThat(Files.readAllBytes(path)).containsExactly(before);
            assertThat(writer.getSecret("new")).isNull();
            assertThat(writer.getSecret("original")).isEqualTo(key(1));
            assertNoTemporaryFiles();
            fail.set(false);
            writer.store("new", key(2));
            try (var reader = new KeyManagerImpl(path, password)) {
                assertThat(reader.getSecret("original")).isEqualTo(key(1));
                assertThat(reader.getSecret("new")).isEqualTo(key(2));
            }
        }
    }

    @Test
    void aPartialWriteCannotTruncateTheExistingFileOrLeaveTemporaryKeyMaterial() throws Exception {
        var path = directory.resolve("keys.p12");
        try (var initial = new KeyManagerImpl(path, password)) { initial.store("original", key(1)); }
        var before = Files.readAllBytes(path);
        var failure = new IOException("disk write failed");
        var files = new AtomicKeyStoreFile((temporary, bytes) -> {
            Files.write(temporary, java.util.Arrays.copyOf(bytes, 8));
            throw failure;
        }, AtomicKeyStoreFile::replace);
        try (var writer = new KeyManagerImpl(path, password, files)) {
            assertThatThrownBy(() -> writer.getOrCreateSecret("new", () -> key(2)))
                    .isInstanceOf(KeyManagerException.class).hasCause(failure);
            assertThat(Files.readAllBytes(path)).containsExactly(before);
            assertThat(writer.getSecret("new")).isNull();
            assertNoTemporaryFiles();
        }
    }

    @Test
    void closeWaitsForAnAcceptedKeyWriteAndThenRejectsAllOperations() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var files = new AtomicKeyStoreFile((temporary, bytes) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("key write was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException(failure);
            }
            AtomicKeyStoreFile.writeAndForce(temporary, bytes);
        }, AtomicKeyStoreFile::replace);
        var writer = new KeyManagerImpl(directory.resolve("keys.p12"), password, files);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var creating = workers.submit(() -> writer.getOrCreateSecret("credential", () -> key(1)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = workers.submit(writer::close);
                assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(creating.get(5, TimeUnit.SECONDS)).isEqualTo(key(1));
                closing.get(5, TimeUnit.SECONDS);
                assertThatThrownBy(() -> writer.getSecret("credential")).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> writer.store("another", key(2))).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> writer.getOrCreateSecret("another", () -> key(2))).isInstanceOf(IllegalStateException.class);
            } finally { release.countDown(); }
        } finally { writer.close(); }
    }

    @Test
    void missingParentsAreCreatedAndKeyFilesUseOwnerOnlyPermissionsWhereSupported() throws Exception {
        var path = directory.resolve("new/deep/keys.p12");
        try (var writer = new KeyManagerImpl(path, password)) { writer.store("credential", key(1)); }
        try (var reader = new KeyManagerImpl(path, password)) { assertThat(reader.getSecret("credential")).isEqualTo(key(1)); }
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(Files.getPosixFilePermissions(path)).isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    void anUnreadableStoreIsNotReplacedWithANewKey() throws Exception {
        var path = directory.resolve("keys.p12");
        var corrupt = new byte[]{1, 2, 3, 4};
        Files.write(path, corrupt);
        assertThatThrownBy(() -> new KeyManagerImpl(path, password)).isInstanceOf(KeyManagerException.class);
        assertThat(Files.readAllBytes(path)).containsExactly(corrupt);
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertThat(files.map(value -> value.getFileName().toString()).filter(value -> value.endsWith(".tmp")).toList()).isEmpty();
        }
    }

    private static SecretKey key(int value) {
        var bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return new SecretKeySpec(bytes, "AES");
    }
}
