package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.app.theme.Theme;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@ExtendWith(ApplicationExtension.class)
class OwnedSettingsProjectionTest {
    @TempDir Path directory;

    @Test
    void anEarlierAcceptedWriteFailurePreventsAnEditorFromReportingSuccessfulPersistence() {
        var node = spy(new MemoryPreferences());
        node.put("theme", "PRIMER");
        var failure = new IllegalStateException("synthetic version write failed");
        doThrow(failure).when(node).put("database.version", "V_1");
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var service = new SettingsSaveService(preferences, filters);
            preferences.getGeneralPreferences().setDatabaseVersion(com.github.k7t3.tcv.database.DatabaseVersion.V_1);

            assertThatThrownBy(() -> service.save(new SettingsDraft(
                    Map.of("theme", Optional.of("NORD")), ChatFilterEntityStore.Changes.empty())))
                    .isInstanceOf(IllegalStateException.class).hasCause(failure);

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(filters.read().revision()).isZero();
        } finally { assertThatThrownBy(preferences::close).hasCause(failure); }
    }

    @Test
    void aDelayedEditorResultDoesNotOverwriteALaterOrdinaryThemeChange(FxRobot robot) {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        try (var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
             var db = new TestDBConnector()) {
            preferences.load();
            robot.interact(() -> preferences.getGeneralPreferences().themeProperty());
            db.connect();
            var saved = new SettingsSaveService(preferences, new ChatFilterEntityStore(db)).save(new SettingsDraft(
                    Map.of("theme", Optional.of("NORD")), ChatFilterEntityStore.Changes.empty()));
            robot.interact(() -> preferences.getGeneralPreferences().setTheme(Theme.CUPERTINO));
            preferences.save();

            robot.interact(() -> {
                preferences.applyCommittedValues(saved.draft().values());
                assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.CUPERTINO);
            });

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
        }
    }

    @Test
    void anEditorCommitUpdatesTheSnapshotBeforeAPropertyIsFirstOpened(FxRobot robot) {
        var node = new MemoryPreferences();
        node.put("chat.show.badges", "true");
        try (var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
             var db = new TestDBConnector()) {
            preferences.load();
            db.connect();
            var saved = new SettingsSaveService(preferences, new ChatFilterEntityStore(db)).save(new SettingsDraft(
                    Map.of("chat.show.badges", Optional.of("false")), ChatFilterEntityStore.Changes.empty()));

            robot.interact(() -> {
                preferences.applyCommittedValues(saved.draft().values());
                assertThat(preferences.getChatPreferences().isShowBadges()).isFalse();
            });

            assertThat(node.getBoolean("chat.show.badges", true)).isFalse();
        }
    }
}
