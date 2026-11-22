package com.github.k7t3.tcv.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLockInterruptionException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class InterruptibleFileLocksTest {
    @TempDir Path directory;

    @Test
    void anAlreadyInterruptedCallerDoesNotAcquireOrCloseTheBorrowedChannel() throws Exception {
        try (var channel = FileChannel.open(directory.resolve("synthetic.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            var result = new CompletableFuture<Throwable>();
            var retained = new AtomicBoolean();
            var worker = Thread.startVirtualThread(() -> {
                Thread.currentThread().interrupt();
                try (var ignored = InterruptibleFileLocks.acquire(channel)) { result.complete(new AssertionError("unexpected lock")); }
                catch (Throwable failure) { result.complete(failure); }
                finally { retained.set(Thread.currentThread().isInterrupted()); }
            });

            assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(FileLockInterruptionException.class);
            worker.join(Duration.ofSeconds(5));

            assertThat(worker.isAlive()).isFalse();
            assertThat(retained).isTrue();
            assertThat(channel.isOpen()).isTrue();
            try (var lock = channel.tryLock()) { assertThat(lock).isNotNull(); }
        }
    }
}
