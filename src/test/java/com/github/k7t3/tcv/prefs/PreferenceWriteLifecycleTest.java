package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class PreferenceWriteLifecycleTest {
    @TempDir Path directory;

    @Test
    void closingWaitsForAnAcceptedWriteAndRejectsLaterChanges() throws Exception {
        var node = spy(new MemoryPreferences());
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            writing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("backend write was not released");
            return invocation.callRealMethod();
        }).when(node).put("database.version", "V_1");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1);
            assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
            var started = new CountDownLatch(1);
            var closed = worker.submit(() -> {
                started.countDown();
                ((AutoCloseable) preferences).close();
                return null;
            });
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> closed.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                closed.get(5, TimeUnit.SECONDS);
                assertThat(node.get("database.version", null)).isEqualTo("V_1");
                assertThatThrownBy(() -> preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");

            } finally { release.countDown(); closed.get(5, TimeUnit.SECONDS); }
        }
    }
}
