package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@ExtendWith(ApplicationExtension.class)
class KeyBindingWriteOwnershipTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"custom", "default", "clear"})
    void aKeyBindingChangeIsVisibleWithoutWaitingForTheBackend(String change, FxRobot robot) throws Exception {
        var binding = KeyBinding.OPEN_PREFERENCES;
        var node = spy(new MemoryPreferences());
        node.put(binding.getId(), "Ctrl+F9");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        var keys = preferences.getKeyBindingPreferences();
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> blocked = invocation -> {
            writing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("backend write was not released");
            return invocation.callRealMethod();
        };
        if (change.equals("custom")) doAnswer(blocked).when(node).put(binding.getId(), "Ctrl+F8");
        else doAnswer(blocked).when(node).remove(binding.getId());
        var changed = new CompletableFuture<KeyCombination>();
        Platform.runLater(() -> {
            try {
                if (change.equals("clear")) keys.clear();
                else keys.storeCombination(binding, change.equals("custom")
                        ? KeyCombination.keyCombination("Ctrl+F8") : binding.getDefaultCombination());
                changed.complete(keys.readCustomCombination(binding));
            } catch (Throwable failure) { changed.completeExceptionally(failure); }
        });
        try {
            assertThat(changed.get(1, TimeUnit.SECONDS)).isEqualTo(change.equals("custom")
                    ? KeyCombination.keyCombination("Ctrl+F8") : KeyCombination.NO_MATCH);
            assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); changed.get(5, TimeUnit.SECONDS); preferences.close(); }
        assertThat(node.get(binding.getId(), null)).isEqualTo(change.equals("custom") ? "Ctrl+F8" : null);
        assertThat(PreferenceProjection.owners(node)).containsKey(binding.getId());
    }
}
