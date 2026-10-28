package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class PreferenceInteractionDuringCredentialWriteTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"live-filter", "floating-opacity"})
    void changingDisplayStateDoesNotWaitForCredentialEncryption(String control, FxRobot robot) throws Exception {
        var node = new MemoryPreferences();
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        robot.interact(() -> {
            preferences.getStatePreferences().onlyLiveProperty();
            preferences.getChatPreferences().floatingChatOpacityProperty();
        });
        var encoding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var store = new PreferencesCredentialStorage(node, bytes -> {
            encoding.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("encoding was not released");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            return bytes.clone();
        }, byte[]::clone, directory.resolve("credentials.lock"), preferences.settingsLockFile().orElseThrow());
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var saved = worker.submit(() -> store.saveCredentials(List.of(new OAuth2Credential(
                    "synthetic", "synthetic-access", null, "owned", "Owned", 3600, List.of(), Map.of()))));
            var changed = new CompletableFuture<Void>();
            var changeQueued = false;
            try {
                assertThat(encoding.await(5, TimeUnit.SECONDS)).isTrue();
                Platform.runLater(() -> {
                    try {
                        if (control.equals("live-filter")) preferences.getStatePreferences().setOnlyLive(true);
                        else preferences.getChatPreferences().setFloatingChatOpacity(0.4);
                        changed.complete(null);
                    } catch (Throwable failure) { changed.completeExceptionally(failure); }
                });
                changeQueued = true;

                changed.get(1, TimeUnit.SECONDS);

            } finally {
                release.countDown();
                saved.get(5, TimeUnit.SECONDS);
                if (changeQueued) changed.get(5, TimeUnit.SECONDS);
                preferences.close();
            }
            if (control.equals("live-filter")) assertThat(node.getBoolean("only.live", false)).isTrue();
            else assertThat(node.getDouble("floatable.chat.opacity", 0)).isEqualTo(0.4);
        }
    }
}
