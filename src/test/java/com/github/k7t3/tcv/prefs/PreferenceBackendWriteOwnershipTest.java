package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@ExtendWith(ApplicationExtension.class)
class PreferenceBackendWriteOwnershipTest {
    @TempDir Path directory;

    @Test
    void anOwnedDisplayChangeRemainsResponsiveWhileItsBackendWriteIsBlocked(FxRobot robot) throws Exception {
        var node = spy(new MemoryPreferences());
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        robot.interact(() -> preferences.getStatePreferences().onlyLiveProperty());
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            writing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("backend write was not released");
            return invocation.callRealMethod();
        }).when(node).put("only.live", "true");
        var changed = new CompletableFuture<Boolean>();
        Platform.runLater(() -> {
            try {
                preferences.getStatePreferences().setOnlyLive(true);
                changed.complete(preferences.getStatePreferences().isOnlyLive());
            } catch (Throwable failure) { changed.completeExceptionally(failure); }
        });
        try {
            assertThat(changed.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            changed.get(5, TimeUnit.SECONDS);
            preferences.save();
        }
        assertThat(node.getBoolean("only.live", false)).isTrue();
    }
}
