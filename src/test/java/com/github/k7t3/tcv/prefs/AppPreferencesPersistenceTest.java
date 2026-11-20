package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.support.MemoryPreferences;
import javafx.scene.text.Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

@ExtendWith(ApplicationExtension.class)
class AppPreferencesPersistenceTest {
    @Test
    void unsetChatFontDefaultsToInstalledHiragino(FxRobot robot) {
        var expectedFamily = Font.getFamilies().contains("Hiragino Sans") ? "Hiragino Sans" : Font.getDefault().getFamily();
        var preferences = new AppPreferences(new MemoryPreferences());

        robot.interact(() -> assertThat(preferences.getChatPreferences().getFont())
                .isEqualTo(new FontSettings(expectedFamily, Font.getDefault().getSize())));
    }

    @Test
    void flushingPreferencesDoesNotRewriteTheImmutableFontConfiguration(FxRobot robot) throws Exception {
        var node = spy(new MemoryPreferences());
        var preferences = new AppPreferences(node);
        var font = new FontSettings("System", 16);
        robot.interact(() -> preferences.getChatPreferences().setFont(font));
        org.mockito.Mockito.clearInvocations(node);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            worker.submit(preferences::save).get(5, TimeUnit.SECONDS);
        }
        org.mockito.Mockito.verify(node, org.mockito.Mockito.never()).put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(FontSettings.deserialize(node.getByteArray("chat.font.family", null))).isEqualTo(font);
    }

    @Test
    void propertyChangesAreAlreadyStoredBeforeFlush(FxRobot robot) {
        var node = new MemoryPreferences();
        var preferences = new AppPreferences(node);
        robot.interact(() -> {
            preferences.getChatPreferences().setShowUserName(false);
            preferences.getChatPreferences().setFloatingChatOpacity(0.4);
            preferences.getChatPreferences().setChatCacheSize(192);
            preferences.getStatePreferences().setOnlyLive(true);
        });
        assertThat(node.getBoolean("chat.show.username", true)).isFalse();
        assertThat(node.getDouble("floatable.chat.opacity", 0)).isEqualTo(0.4);
        assertThat(node.getInt("chat.cache.size", 0)).isEqualTo(192);
        assertThat(node.getBoolean("only.live", false)).isTrue();
    }
}
