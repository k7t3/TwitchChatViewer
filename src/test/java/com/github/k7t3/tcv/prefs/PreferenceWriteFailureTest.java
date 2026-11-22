package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class PreferenceWriteFailureTest {
    @TempDir Path directory;

    @Test
    void aRejectedPreferenceWriterCannotMakeSavingTheUnwrittenValueSucceed() {
        var node = new MemoryPreferences();
        var rejected = new RejectedExecutionException("synthetic preference writer rejected");
        var attempts = new AtomicInteger();
        var store = new PreferenceStore(node, directory.resolve("settings.lock"), command -> {
            if (attempts.getAndIncrement() == 0) throw rejected;
            command.run();
        });

        store.write("database.version", Optional.of("V_1"), false);

        assertThatThrownBy(store::save).isInstanceOf(IllegalStateException.class).hasCause(rejected);
        assertThat(node.get("database.version", null)).isNull();
    }

    @Test
    void anAcceptedBackendCompletionFailureKeepsItsOriginalExceptionIdentity() {
        var node = spy(new MemoryPreferences());
        var writeFailure = new CompletionException("synthetic backend completion failed", new IllegalArgumentException("backend cause"));
        doThrow(writeFailure).when(node).put("database.version", "V_1");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));

        preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1);

        assertThatThrownBy(preferences::close).isInstanceOf(IllegalStateException.class).hasCause(writeFailure);
    }

    @Test
    void aWriterStartupErrorIsPreservedWhenClosingUnwrittenPreferences() {
        var node = new MemoryPreferences();
        var startupFailure = new OutOfMemoryError("synthetic writer startup failed");
        var attempts = new AtomicInteger();
        var store = new PreferenceStore(node, directory.resolve("settings.lock"), command -> {
            if (attempts.getAndIncrement() == 0) throw startupFailure;
            command.run();
        });

        store.write("database.version", Optional.of("V_1"), false);

        assertThatThrownBy(() -> store.close(store::save)).isInstanceOf(IllegalStateException.class).hasCause(startupFailure);
        assertThat(node.get("database.version", null)).isNull();
    }

    @Test
    void aRejectedReadDoesNotPoisonALaterAcceptedWrite() {
        var node = new MemoryPreferences();
        var rejected = new RejectedExecutionException("synthetic read rejected");
        var attempts = new AtomicInteger();
        var store = new PreferenceStore(node, directory.resolve("settings.lock"), command -> {
            if (attempts.getAndIncrement() == 0) throw rejected;
            command.run();
        });
        assertThatThrownBy(() -> store.exclusive(() -> "unused read")).isSameAs(rejected);

        store.write("database.version", Optional.of("V_1"), false);
        store.close(store::save);

        assertThat(node.get("database.version", null)).isEqualTo("V_1");
    }

    @Test
    void rejectingTheFinalWriterCannotHideTheEarlierAcceptedWriteFailure() {
        var writeFailure = new RejectedExecutionException("synthetic write startup failed");
        var closeFailure = new RejectedExecutionException("synthetic closing writer failed");
        var attempts = new AtomicInteger();
        var store = new PreferenceStore(new MemoryPreferences(), directory.resolve("settings.lock"), command -> {
            throw attempts.getAndIncrement() == 0 ? writeFailure : closeFailure;
        });
        store.write("database.version", Optional.of("V_1"), false);

        assertThatThrownBy(() -> store.close(store::save)).isInstanceOf(IllegalStateException.class)
                .hasCause(writeFailure).satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(closeFailure));
    }

    @Test
    void failingToReacquireStorageDuringCloseAlsoPreservesTheEarlierWriteFailure() throws Exception {
        var node = spy(new MemoryPreferences());
        var writeFailure = new IllegalStateException("synthetic write failed");
        doThrow(writeFailure).when(node).put("database.version", "V_1");
        var lock = directory.resolve("settings.lock");
        var preferences = new AppPreferences(node, lock);
        preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1);
        assertThatThrownBy(() -> preferences.exclusive(() -> null)).hasCause(writeFailure);
        Files.delete(lock);
        Files.createDirectory(lock);

        assertThatThrownBy(preferences::close).isInstanceOf(IllegalStateException.class).hasCause(writeFailure)
                .satisfies(failure -> org.assertj.core.api.Assertions.assertThat(failure.getSuppressed())
                        .singleElement().isInstanceOf(java.io.UncheckedIOException.class));
    }

    @Test
    void theFinalFlushCannotHideAnEarlierAcceptedWriteFailure() throws Exception {
        var node = spy(new MemoryPreferences());
        var writeFailure = new IllegalStateException("synthetic write failed");
        var flushFailure = new IllegalStateException("synthetic flush failed");
        doThrow(writeFailure).when(node).put("database.version", "V_1");
        doThrow(flushFailure).when(node).flush();
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));

        preferences.getGeneralPreferences().setDatabaseVersion(DatabaseVersion.V_1);

        assertThatThrownBy(preferences::close).isInstanceOf(IllegalStateException.class)
                .hasCause(writeFailure).satisfies(failure -> org.assertj.core.api.Assertions.assertThat(failure.getSuppressed())
                        .containsExactly(flushFailure));
    }
}
