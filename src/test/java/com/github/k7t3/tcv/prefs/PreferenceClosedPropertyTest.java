package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ApplicationExtension.class)
class PreferenceClosedPropertyTest {
    @TempDir Path directory;

    @Test
    void aClosedModelRejectsAChangeBeforeChangingItsVisibleValue(FxRobot robot) {
        var node = new MemoryPreferences();
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        robot.interact(() -> preferences.getStatePreferences().onlyLiveProperty());
        preferences.close();

        robot.interact(() -> {
            assertThatThrownBy(() -> preferences.getStatePreferences().setOnlyLive(true))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            assertThat(preferences.getStatePreferences().isOnlyLive()).isFalse();
        });

        assertThat(node.getBoolean("only.live", false)).isFalse();
    }
}
