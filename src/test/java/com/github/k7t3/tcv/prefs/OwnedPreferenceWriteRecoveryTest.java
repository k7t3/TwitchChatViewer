package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.prefs.SettingsSaveService;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.entity.service.SettingsJournal;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class OwnedPreferenceWriteRecoveryTest {
    @TempDir Path directory;

    @Test
    void failedCompensationPreservesTheWriteValueOwnershipAndFlushFailures() throws Exception {
        var node = spy(new MemoryPreferences());
        node.put("theme", "NORD");
        node.put("unrelated", "synthetic-preserved");
        PreferenceProjection.claim(node, UUID.randomUUID(), Map.of("theme", Optional.of("NORD")));
        var owners = node.get("settings.projection.owners", null);
        var writeFailure = new IllegalStateException("synthetic write failed");
        var valueFailure = new IllegalStateException("synthetic value compensation failed");
        var ownerFailure = new IllegalStateException("synthetic ownership compensation failed");
        var flushFailure = new IllegalStateException("synthetic compensation flush failed");
        doThrow(writeFailure).when(node).put("theme", "CUPERTINO");
        doThrow(valueFailure).when(node).put("theme", "NORD");
        doThrow(ownerFailure).when(node).put("settings.projection.owners", owners);
        doThrow(flushFailure).when(node).flush();
        var preferences = new AppPreferences(node, directory.resolve("settings.lock"));

        preferences.getGeneralPreferences().setTheme(Theme.CUPERTINO);

        assertThatThrownBy(preferences::close).hasCause(writeFailure);
        assertThat(writeFailure.getSuppressed()).containsExactly(valueFailure);
        assertThat(valueFailure.getSuppressed()).containsExactly(ownerFailure, flushFailure);
        assertThat(node.get("unrelated", null)).isEqualTo("synthetic-preserved");
    }

    @ParameterizedTest
    @ValueSource(strings = {"put", "flush"})
    void aFailedOrdinaryWriteCannotPreventRecoveryOfAnUncommittedEditorProjection(String failingStep) throws Exception {
        var node = spy(new MemoryPreferences());
        node.put("theme", "NORD");
        var operation = UUID.randomUUID();
        PreferenceProjection.claim(node, operation, Map.of("theme", Optional.of("NORD")));
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var journal = filters.settingsJournal();
            journal.exclusive(() -> {
                journal.prepare(new SettingsJournal.Entry(operation, node.absolutePath(), node.isUserNode(),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("PRIMER"))),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))), false));
                return null;
            });
            var preferences = new AppPreferences(node, directory.resolve("settings.lock"));
            assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(Theme.NORD);
            var failure = new IllegalStateException("synthetic ordinary preference write failed");
            if (failingStep.equals("put")) doThrow(failure).doCallRealMethod().when(node).put("theme", "CUPERTINO");
            else doThrow(failure).doCallRealMethod().when(node).flush();

            preferences.getGeneralPreferences().setTheme(Theme.CUPERTINO);
            assertThatThrownBy(preferences::close).hasCause(failure);
            try (var restarted = new AppPreferences(node, directory.resolve("settings.lock"))) {
                new SettingsSaveService(restarted, filters).recover();

                assertThat(node.get("theme", null)).isEqualTo("PRIMER");
                assertThat(journal.exclusive(journal::pending)).isEmpty();
            }
        }
    }
}
