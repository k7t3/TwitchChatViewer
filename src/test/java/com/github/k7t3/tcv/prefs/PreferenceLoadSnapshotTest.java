package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(ApplicationExtension.class)
class PreferenceLoadSnapshotTest {
    @TempDir Path directory;

    @Test
    void loadingKnownSettingsLetsFxInitializePropertiesWithoutReadingStorageOrCredentials(FxRobot robot) throws Exception {
        var node = spy(new MemoryPreferences());
        node.put("theme", "NORD");
        node.put("chat.show.badges", "false");
        node.put("only.live", "true");
        node.put("database.version", "V_1");
        node.put("keybind.preferences", "Ctrl+F8");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));

        preferences.load();

        verify(node, never()).get("credentials", null);
        verify(node, never()).keys();
        doThrow(new IllegalStateException("backend read after loading")).when(node).get(anyString(), isNull());
        try {
            robot.interact(() -> {
                assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.NORD);
                assertThat(preferences.getGeneralPreferences().getDatabaseVersion()).isEqualTo(DatabaseVersion.V_1);
                assertThat(preferences.getChatPreferences().isShowBadges()).isFalse();
                assertThat(preferences.getStatePreferences().isOnlyLive()).isTrue();
                assertThat(preferences.getKeyBindingPreferences().readCustomCombination(KeyBinding.OPEN_PREFERENCES))
                        .isEqualTo(KeyCombination.keyCombination("Ctrl+F8"));
            });
        } finally { preferences.close(); }
    }
}
