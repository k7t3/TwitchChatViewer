package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.entity.service.SettingsJournal;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.PreferenceProjection;
import com.github.k7t3.tcv.prefs.PreferenceValuesCodec;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class SettingsProfileRecoveryTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test
    void aFailedSaveInAnotherDatabaseCannotSupersedeAnUncommittedProjection() throws Exception {
        var node = spy(new MemoryPreferences());
        node.put("theme", "NORD");
        var operation = UUID.randomUUID();
        PreferenceProjection.claim(node, operation,
                Map.of("theme", Optional.of("NORD")));
        try (var first = new TestDBConnector(); var second = new TestDBConnector();
             var preferences = new AppPreferences(node, directory.resolve("settings.lock"))) {
            first.connect();
            second.connect();
            var firstFilters = new ChatFilterEntityStore(first);
            var journal = prepareUncommittedTheme(firstFilters, node, operation);
            var failure = new IllegalStateException("synthetic second profile flush failed");
            doThrow(failure).doCallRealMethod().when(node).flush();

            assertThatThrownBy(() ->
                    new SettingsSaveService(preferences, new ChatFilterEntityStore(second)).save(draft("CUPERTINO")))
                    .isSameAs(failure);
            new SettingsSaveService(preferences, firstFilters).recover();

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(journal.exclusive(journal::pending)).isEmpty();
        }
    }

    @Test
    void aPartlyPublishedOwnershipFailureCannotSupersedeAnUncommittedProjection() {
        var node = spy(new MemoryPreferences());
        node.put("theme", "NORD");
        var operation = UUID.randomUUID();
        PreferenceProjection.claim(node, operation,
                Map.of("theme", Optional.of("NORD")));
        try (var first = new TestDBConnector(); var second = new TestDBConnector();
             var preferences = new AppPreferences(node, directory.resolve("settings.lock"))) {
            first.connect();
            second.connect();
            var firstFilters = new ChatFilterEntityStore(first);
            var journal = prepareUncommittedTheme(firstFilters, node, operation);
            var failure = new IllegalStateException("synthetic partial ownership write failed");
            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw failure;
            }).doCallRealMethod().when(node).put(eq("settings.projection.owners"), anyString());

            assertThatThrownBy(() ->
                    new SettingsSaveService(preferences, new ChatFilterEntityStore(second)).save(draft("CUPERTINO")))
                    .isSameAs(failure);
            new SettingsSaveService(preferences, firstFilters).recover();

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(journal.exclusive(journal::pending)).isEmpty();
        }
    }

    @Test
    void recoveringAFailedCompensationInAnotherDatabasePreservesTheEarlierRecovery() throws Exception {
        var node = spy(new MemoryPreferences());
        var firstPath = directory.resolve("first.db");
        var secondPath = directory.resolve("second.db");
        leaveFailedSecondProfile(node, firstPath, secondPath);
        try (var first = new com.github.k7t3.tcv.database.SQLiteDBConnector(firstPath);
             var second = new com.github.k7t3.tcv.database.SQLiteDBConnector(secondPath)) {
            first.connect();
            second.connect();
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).recover();
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first)).recover();

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
        }
    }

    @Test
    void reverseRecoveryOrderKeepsTheEarlierJournalUntilTheLaterProjectionIsRecovered() throws Exception {
        var node = spy(new MemoryPreferences());
        var firstPath = directory.resolve("first.db");
        var secondPath = directory.resolve("second.db");
        leaveFailedSecondProfile(node, firstPath, secondPath);
        try (var first = new com.github.k7t3.tcv.database.SQLiteDBConnector(firstPath);
             var second = new com.github.k7t3.tcv.database.SQLiteDBConnector(secondPath)) {
            first.connect();
            second.connect();
            var firstFilters = new ChatFilterEntityStore(first);
            var journal = firstFilters.settingsJournal();
            var recovery = new SettingsSaveService(new AppPreferences(node), firstFilters);

            assertThatThrownBy(recovery::recover).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("pending settings projection");

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
            assertThat(journal.exclusive(journal::pending)).isPresent();
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).recover();
            recovery.recover();
            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(journal.exclusive(journal::pending)).isEmpty();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"truncated", "invalid-owner", "absent-owner"})
    void invalidPreviousOwnershipCannotProjectOrDiscardRecoveryData(String corruption) throws Exception {
        var node = spy(new MemoryPreferences());
        var firstPath = directory.resolve("first.db");
        var secondPath = directory.resolve("second.db");
        leaveFailedSecondProfile(node, firstPath, secondPath);
        try (var first = new com.github.k7t3.tcv.database.SQLiteDBConnector(firstPath);
             var second = new com.github.k7t3.tcv.database.SQLiteDBConnector(secondPath)) {
            first.connect();
            second.connect();
            var filters = new ChatFilterEntityStore(second);
            var journal = filters.settingsJournal();
            var original = journal.exclusive(() -> journal.pending().orElseThrow().previousOwners().orElseThrow());
            var invalid = switch (corruption) {
                case "truncated" -> new byte[] {0};
                case "invalid-owner" -> PreferenceValuesCodec.encode(Map.of("theme", Optional.of("not-a-uuid")));
                case "absent-owner" -> PreferenceValuesCodec.encode(Map.of("theme", Optional.empty()));
                default -> throw new AssertionError(corruption);
            };
            replacePreviousOwners(second, invalid);
            var recovery = new SettingsSaveService(new AppPreferences(node), filters);

            assertThatThrownBy(recovery::recover).isInstanceOf(IllegalArgumentException.class);

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
            assertThat(journal.exclusive(journal::pending)).isPresent();
            replacePreviousOwners(second, original);
            recovery.recover();
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first)).recover();
            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
        }
    }

    @Test
    void anUnsupportedPendingMarkerCannotBeDiscardedByItsOwnRecovery() throws Exception {
        var node = spy(new MemoryPreferences());
        var firstPath = directory.resolve("first.db");
        var secondPath = directory.resolve("second.db");
        leaveFailedSecondProfile(node, firstPath, secondPath);
        try (var first = new com.github.k7t3.tcv.database.SQLiteDBConnector(firstPath);
             var second = new com.github.k7t3.tcv.database.SQLiteDBConnector(secondPath)) {
            first.connect();
            second.connect();
            var filters = new ChatFilterEntityStore(second);
            var journal = filters.settingsJournal();
            var operation = journal.exclusive(() -> journal.pending().orElseThrow().operation());
            var marker = "settings.projection.pending." + operation;
            node.put(marker, "unsupported-version");
            var recovery = new SettingsSaveService(new AppPreferences(node), filters);

            assertThatThrownBy(recovery::recover).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid pending settings projection");

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
            assertThat(node.get(marker, null)).isEqualTo("unsupported-version");
            assertThat(journal.exclusive(journal::pending)).isPresent();
            node.put(marker, "1");
            recovery.recover();
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first)).recover();
            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
        }
    }

    @Test
    void aPreparedProjectionCannotUndoRecoveryThatRemovedItsPreviousOwner() {
        var node = new MemoryPreferences();
        node.put("theme", "NORD");
        var firstOperation = UUID.randomUUID();
        PreferenceProjection.beginProjection(node, firstOperation);
        PreferenceProjection.claim(node, firstOperation, Map.of("theme", Optional.of("NORD")));
        try (var first = new TestDBConnector(); var second = new TestDBConnector()) {
            first.connect();
            second.connect();
            var firstFilters = new ChatFilterEntityStore(first);
            var secondFilters = new ChatFilterEntityStore(second);
            var firstJournal = firstFilters.settingsJournal();
            firstJournal.exclusive(() -> {
                firstJournal.prepare(new SettingsJournal.Entry(firstOperation, node.absolutePath(), node.isUserNode(),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("PRIMER"))),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))), false,
                        Optional.of(PreferenceValuesCodec.encode(Map.of()))));
                return null;
            });
            var secondJournal = secondFilters.settingsJournal();
            secondJournal.exclusive(() -> {
                secondJournal.prepare(new SettingsJournal.Entry(UUID.randomUUID(), node.absolutePath(), node.isUserNode(),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("CUPERTINO"))), false,
                        Optional.of(PreferenceValuesCodec.encode(PreferenceProjection.owners(node)))));
                return null;
            });

            new SettingsSaveService(new AppPreferences(node), firstFilters).recover();
            new SettingsSaveService(new AppPreferences(node), secondFilters).recover();

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(PreferenceProjection.owners(node)).isEmpty();
            assertThat(secondJournal.exclusive(secondJournal::pending)).isEmpty();
        }
    }

    @Test
    void restoringOneProjectionPreservesNewerOwnershipOfItsOtherKeys() throws Exception {
        var node = spy(new MemoryPreferences());
        node.put("chat.show.badges", "true");
        var operation = UUID.randomUUID();
        PreferenceProjection.claim(node, operation, Map.of("chat.show.badges", Optional.of("true")));
        var firstPath = directory.resolve("first.db");
        var secondPath = directory.resolve("second.db");
        try (var old = new TestDBConnector(); var newer = new TestDBConnector()) {
            old.connect();
            newer.connect();
            var oldFilters = new ChatFilterEntityStore(old);
            var journal = oldFilters.settingsJournal();
            journal.exclusive(() -> {
                journal.prepare(new SettingsJournal.Entry(operation, node.absolutePath(), node.isUserNode(),
                        PreferenceValuesCodec.encode(Map.of("chat.show.badges", Optional.of("false"))),
                        PreferenceValuesCodec.encode(Map.of("chat.show.badges", Optional.of("true"))), false));
                old.transaction(() -> journal.markCommitted(operation));
                return null;
            });
            leaveFailedSecondProfile(node, firstPath, secondPath);
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(newer)).save(
                    new SettingsDraft(Map.of("chat.show.badges", Optional.of("false")), ChatFilterEntityStore.Changes.empty()));

            try (var first = new com.github.k7t3.tcv.database.SQLiteDBConnector(firstPath);
                 var second = new com.github.k7t3.tcv.database.SQLiteDBConnector(secondPath)) {
                first.connect();
                second.connect();
                new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).recover();
                new SettingsSaveService(new AppPreferences(node), oldFilters).recover();
                new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first)).recover();
            }

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(node.getBoolean("chat.show.badges", true)).isFalse();
        }
    }

    @Test
    void recoveringAnOldEditorSavePreservesALaterPropertyChange() {
        var node = new MemoryPreferences();
        node.put("chat.show.badges", "true");
        var preferences = new AppPreferences(node);
        try (var connector = spy(new TestDBConnector())) {
            connector.connect();
            rejectCleanupOnce(connector);
            var filters = new ChatFilterEntityStore(connector);
            var service = new SettingsSaveService(preferences, filters);
            service.save(new SettingsDraft(Map.of("chat.show.badges", Optional.of("false")),
                    ChatFilterEntityStore.Changes.empty()));
            preferences.getChatPreferences().setShowBadges(true);
            preferences.save();

            service.recover();

            assertThat(node.getBoolean("chat.show.badges", false)).isTrue();
            var journal = filters.settingsJournal();
            assertThat(journal.exclusive(journal::pending)).isEmpty();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void recoveryStillUnderstandsRecordsWrittenBeforeOwnershipWasTracked(boolean committed) {
        var node = new MemoryPreferences();
        node.put("theme", committed ? "PRIMER" : "NORD");
        node.put("unrelated", "preserved");
        try (var connector = new TestDBConnector()) {
            connector.connect();
            var filters = new ChatFilterEntityStore(connector);
            var journal = filters.settingsJournal();
            var operation = java.util.UUID.randomUUID();
            journal.exclusive(() -> {
                journal.prepare(new com.github.k7t3.tcv.entity.service.SettingsJournal.Entry(operation, node.absolutePath(),
                        node.isUserNode(), PreferenceValuesCodec.encode(Map.of("theme", Optional.of("PRIMER"))),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))), false));
                if (committed) connector.transaction(() -> journal.markCommitted(operation));
                return null;
            });

            new SettingsSaveService(new AppPreferences(node), filters).recover();

            assertThat(node.get("theme", null)).isEqualTo(committed ? "NORD" : "PRIMER");
            assertThat(node.get("unrelated", null)).isEqualTo("preserved");
            assertThat(journal.exclusive(journal::pending)).isEmpty();
        }
    }

    @Test
    void configuringASharedLockDoesNotCreateItUntilStorageIsUsed() {
        var lock = directory.resolve("not-created/settings.lock");
        var preferences = new AppPreferences(new MemoryPreferences(), lock);
        try (var connector = new TestDBConnector()) {
            new SettingsSaveService(preferences, new ChatFilterEntityStore(connector));

            assertThat(java.nio.file.Files.exists(lock.getParent())).isFalse();
            assertThat(connector.isConnected()).isFalse();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"base64", "payload", "operation"})
    void invalidOwnershipDataCannotProjectOrDiscardAPendingRecord(String corruption) {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        try (var connector = spy(new TestDBConnector())) {
            connector.connect();
            rejectCleanupOnce(connector);
            var filters = new ChatFilterEntityStore(connector);
            var service = new SettingsSaveService(new AppPreferences(node), filters);
            service.save(draft("NORD"));
            var encoded = node.get("settings.projection.owners", null);
            var invalid = switch (corruption) {
                case "base64" -> "?not-base64";
                case "payload" -> java.util.Base64.getEncoder().encodeToString(new byte[] {0});
                case "operation" -> java.util.Base64.getEncoder().encodeToString(PreferenceValuesCodec.encode(
                        Map.of("theme", Optional.of("not-an-operation"))));
                default -> throw new AssertionError(corruption);
            };
            node.put("settings.projection.owners", invalid);
            node.put("theme", "PRIMER");

            org.assertj.core.api.Assertions.assertThatThrownBy(service::recover).isInstanceOf(IllegalArgumentException.class);

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            var journal = filters.settingsJournal();
            assertThat(journal.exclusive(journal::pending)).isPresent();
            node.put("settings.projection.owners", encoded);
            service.recover();
            assertThat(node.get("theme", null)).isEqualTo("NORD");
        }
    }

    @Test
    void anUncommittedOldRecordCannotRollBackANewerSaveFromAnotherDatabase() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var oldPreferences = spy(new AppPreferences(node));
        var failure = new IllegalStateException("old profile flush failed");
        doThrow(failure).when(oldPreferences).save();
        try (var first = new TestDBConnector(); var second = new TestDBConnector()) {
            first.connect();
            second.connect();
            var oldProfile = new SettingsSaveService(oldPreferences, new ChatFilterEntityStore(first));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> oldProfile.save(draft("NORD"))).isSameAs(failure);
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).save(draft("CUPERTINO"));

            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first)).recover();

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
        }
    }

    @Test
    void aNewerSaveEqualToTheOldValueStillSupersedesTheOldRecord() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        try (var first = spy(new TestDBConnector()); var second = new TestDBConnector()) {
            first.connect();
            second.connect();
            rejectCleanupOnce(first);
            var oldProfile = new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first));
            oldProfile.save(draft("NORD"));
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).save(draft("PRIMER"));

            oldProfile.recover();

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
        }
    }

    @Test
    void recoveryStillProjectsItsOtherKeysWhenOnlyOneWasSuperseded() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        node.put("chat.show.badges", "true");
        try (var first = spy(new TestDBConnector()); var second = new TestDBConnector()) {
            first.connect();
            second.connect();
            rejectCleanupOnce(first);
            var oldProfile = new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first));
            oldProfile.save(new SettingsDraft(Map.of("theme", Optional.of("NORD"), "chat.show.badges", Optional.of("false")),
                    ChatFilterEntityStore.Changes.empty()));
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).save(draft("CUPERTINO"));
            node.put("chat.show.badges", "true");

            oldProfile.recover();

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
            assertThat(node.get("chat.show.badges", null)).isEqualTo("false");
        }
    }

    @Test
    void aLaterRemovalCannotBeUndoneByAnOldCommittedRecord() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        try (var first = spy(new TestDBConnector()); var second = new TestDBConnector()) {
            first.connect();
            second.connect();
            rejectCleanupOnce(first);
            var oldProfile = new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first));
            oldProfile.save(draft("NORD"));
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).save(
                    new SettingsDraft(Map.of("theme", Optional.empty()), ChatFilterEntityStore.Changes.empty()));

            oldProfile.recover();

            assertThat(node.get("theme", null)).isNull();
        }
    }

    @Test
    void anOldCommittedRecordCannotReplaceANewerSaveFromAnotherDatabase() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var failCleanup = new AtomicBoolean(true);
        try (var first = spy(new TestDBConnector()); var second = new TestDBConnector()) {
            first.connect();
            second.connect();
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.startsWith("delete from settings_recovery") && failCleanup.getAndSet(false)) {
                    throw new IllegalStateException("first profile cleanup failed");
                }
                return invocation.callRealMethod();
            }).when(first).prepared(anyString(), any());
            var oldProfile = new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(first));
            oldProfile.save(draft("NORD"));
            new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(second)).save(draft("CUPERTINO"));

            oldProfile.recover();

            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
            oldProfile.recover();
            assertThat(node.get("theme", null)).isEqualTo("CUPERTINO");
        }
    }

    private static SettingsDraft draft(String theme) {
        return new SettingsDraft(Map.of("theme", Optional.of(theme)), ChatFilterEntityStore.Changes.empty());
    }

    private static void replacePreviousOwners(com.github.k7t3.tcv.database.DBConnector db, byte[] bytes) {
        db.transaction(() -> db.prepared("update settings_recovery_owners set before_owners = ?;", statement -> {
            statement.setBytes(1, bytes);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }));
    }

    private static void leaveFailedSecondProfile(MemoryPreferences node, java.nio.file.Path firstPath,
                                                 java.nio.file.Path secondPath) throws Exception {
        node.put("theme", "NORD");
        var operation = UUID.randomUUID();
        PreferenceProjection.claim(node, operation, Map.of("theme", Optional.of("NORD")));
        try (var first = new com.github.k7t3.tcv.database.SQLiteDBConnector(firstPath);
             var second = new com.github.k7t3.tcv.database.SQLiteDBConnector(secondPath)) {
            first.connect();
            second.connect();
            prepareUncommittedTheme(new ChatFilterEntityStore(first), node, operation);
            var failure = new IllegalStateException("synthetic second profile flush failed");
            var restore = new IllegalStateException("synthetic second profile compensation failed");
            doThrow(failure).doCallRealMethod().when(node).flush();
            doThrow(restore).doCallRealMethod().when(node).put("theme", "NORD");
            assertThatThrownBy(() -> new SettingsSaveService(new AppPreferences(node),
                    new ChatFilterEntityStore(second)).save(draft("CUPERTINO")))
                    .isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(restore));
        }
    }

    private static SettingsJournal prepareUncommittedTheme(ChatFilterEntityStore filters, MemoryPreferences node,
                                                           UUID operation) {
        var journal = filters.settingsJournal();
        journal.exclusive(() -> {
            journal.prepare(new SettingsJournal.Entry(operation, node.absolutePath(), node.isUserNode(),
                    PreferenceValuesCodec.encode(Map.of("theme", Optional.of("PRIMER"))),
                    PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))), false));
            return null;
        });
        return journal;
    }

    private static void rejectCleanupOnce(TestDBConnector connector) {
        var once = new AtomicBoolean(true);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("delete from settings_recovery") && once.getAndSet(false)) {
                throw new IllegalStateException("old profile cleanup failed");
            }
            return invocation.callRealMethod();
        }).when(connector).prepared(anyString(), any());
    }
}
