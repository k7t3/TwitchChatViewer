package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(org.testfx.framework.junit5.ApplicationExtension.class)
class SettingsSaveServiceTest {
    private SettingsDraft draft(String theme) {
        return new SettingsDraft(Map.of("theme", Optional.of(theme)), new ChatFilterEntityStore.Changes(
                List.of(new ChatKeywordFilterEntity(0, 0, "blocked")), Set.of(), List.of(), Set.of()));
    }

    @Test void aCommitAcknowledgementFailureCannotCompensateAlreadyCommittedPreferences() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var prefs = spy(new AppPreferences(node));
        var flushed = new java.util.concurrent.atomic.AtomicBoolean();
        var rejectOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var failure = new IllegalStateException("commit acknowledgement failed");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            flushed.set(true);
            return null;
        }).when(prefs).save();
        try (var db = spy(new TestDBConnector())) {
            db.connect();
            doAnswer(invocation -> {
                invocation.callRealMethod();
                if (flushed.get() && rejectOnce.getAndSet(false)) throw failure;
                return null;
            }).when(db).commit();
            var filters = new ChatFilterEntityStore(db);
            var service = new SettingsSaveService(prefs, filters);

            assertThatThrownBy(() -> service.save(draft("NORD"))).isSameAs(failure);

            assertThat(node.get("theme", null)).isEqualTo("NORD");
            assertThat(filters.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
            node.put("theme", "PRIMER");
            service.recover();
            assertThat(node.get("theme", null)).isEqualTo("NORD");
        }
    }

    @Test void aJournalCleanupFailureDoesNotReportAnAlreadyCommittedSaveAsFailed() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var cleanupFailure = new IllegalStateException("journal cleanup failed");
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var db = spy(new TestDBConnector())) {
            db.connect();
            doAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.startsWith("delete from settings_recovery") && failOnce.getAndSet(false)) throw cleanupFailure;
                return invocation.callRealMethod();
            }).when(db).prepared(anyString(), any());
            var filters = new ChatFilterEntityStore(db);
            var service = new SettingsSaveService(new AppPreferences(node), filters);

            var saved = service.save(draft("NORD"));

            assertThat(node.get("theme", null)).isEqualTo("NORD");
            assertThat(saved.filters().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
            assertThat(saved.filters().revision()).isEqualTo(1);
            node.put("theme", "PRIMER");
            service.recover();
            assertThat(node.get("theme", null)).isEqualTo("NORD");
            assertThat(filters.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
        }
    }

    @Test void successPersistsBothStoresWithoutMutatingCachedProperties() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        node.put("credential", "untouched");
        var prefs = new AppPreferences(node);
        var cached = prefs.getGeneralPreferences().getTheme();
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var saved = new SettingsSaveService(prefs, filters).save(draft("NORD"));
            assertThat(node.get("theme", null)).isEqualTo("NORD");
            assertThat(node.get("credential", null)).isEqualTo("untouched");
            assertThat(prefs.getGeneralPreferences().getTheme()).isEqualTo(cached);
            assertThat(saved.filters()).isEqualTo(filters.read());
            assertThat(saved.filters().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
        }
    }

    @Test void aPreferenceFlushFailureRestoresRawKeysAndRollsBackSql() {
        var node = new MemoryPreferences();
        var prefs = spy(new AppPreferences(node));
        var failure = new IllegalStateException("flush failed");
        doThrow(failure).doCallRealMethod().when(prefs).save();
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var service = new SettingsSaveService(prefs, filters);
            assertThatThrownBy(() -> service.save(draft("NORD"))).isSameAs(failure);
            assertThat(node.get("theme", null)).isNull();
            assertThat(filters.read().keywords()).isEmpty();
            assertThat(service.save(draft("NORD")).filters().revision()).isEqualTo(1);
        }
    }

    @Test void aSqlCommitFailureRestoresPreferencesAlreadyFlushed() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var prefs = spy(new AppPreferences(node));
        var preferencesFlushed = new java.util.concurrent.atomic.AtomicBoolean();
        var rejectCommitOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            preferencesFlushed.set(true);
            return null;
        }).when(prefs).save();
        try (var db = spy(new TestDBConnector())) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var failure = new IllegalStateException("commit failed");
            doAnswer(invocation -> {
                if (preferencesFlushed.get() && rejectCommitOnce.getAndSet(false)) throw failure;
                return invocation.callRealMethod();
            }).when(db).commit();
            assertThatThrownBy(() -> new SettingsSaveService(prefs, filters).save(draft("NORD"))).isSameAs(failure);
            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(filters.read().keywords()).isEmpty();
            verify(prefs, times(2)).save();
        }
    }

    @Test void aCompensationFailureIsSuppressedOnTheOriginalFailure() {
        var prefs = spy(new AppPreferences(new MemoryPreferences()));
        var failure = new IllegalStateException("save failed");
        var compensation = new IllegalStateException("restore failed");
        doThrow(failure).doThrow(compensation).when(prefs).save();
        try (var db = new TestDBConnector()) {
            db.connect();
            assertThatThrownBy(() -> new SettingsSaveService(prefs, new ChatFilterEntityStore(db)).save(draft("NORD")))
                    .isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(compensation));
        }
    }

    @Test void anIncompleteCompensationRemainsRecoverableAfterAnEarlierSuccessfulSave() throws Exception {
        var node = spy(new MemoryPreferences());
        var failure = new IllegalStateException("synthetic save flush failed");
        var restore = new IllegalStateException("synthetic restore write failed");
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var service = new SettingsSaveService(new AppPreferences(node), filters);
            service.save(draft("PRIMER"));
            doThrow(failure).doCallRealMethod().when(node).flush();
            doThrow(restore).doCallRealMethod().when(node).put("theme", "PRIMER");

            assertThatThrownBy(() -> service.save(draft("NORD")))
                    .isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(restore));
            var journal = filters.settingsJournal();
            assertThat(journal.exclusive(journal::pending)).isPresent();
            service.recover();

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(journal.exclusive(journal::pending)).isEmpty();
            assertThat(filters.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
        }
    }

    @Test void anEarlierSuppressedDiagnosticDoesNotPreventPreferenceCompensation() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var prefs = spy(new AppPreferences(node));
        var failure = new IllegalStateException("preference flush failed");
        var diagnostic = new IllegalArgumentException("earlier diagnostic");
        failure.addSuppressed(diagnostic);
        doThrow(failure).doCallRealMethod().when(prefs).save();
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);

            assertThatThrownBy(() -> new SettingsSaveService(prefs, filters).save(draft("NORD")))
                    .isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(diagnostic));

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(filters.read().keywords()).isEmpty();
            verify(prefs, times(2)).save();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"theme", "chat.show.username"})
    void compensationAttemptsEveryKeyAndFlushEvenIfOneRestoreWriteFails(String rejectedKey) {
        var node = spy(new MemoryPreferences());
        node.put("theme", "PRIMER");
        node.put("chat.show.username", "true");
        var prefs = spy(new AppPreferences(node));
        var failure = new IllegalStateException("save failed");
        var restore = new IllegalStateException("one restore write failed");
        doThrow(failure).doCallRealMethod().when(prefs).save();
        doThrow(restore).when(node).put(eq(rejectedKey), eq(rejectedKey.equals("theme") ? "PRIMER" : "true"));
        var values = new SettingsDraft(Map.of("theme", Optional.of("NORD"), "chat.show.username", Optional.of("false")), draft("NORD").filters());
        try (var db = new TestDBConnector()) {
            db.connect();
            assertThatThrownBy(() -> new SettingsSaveService(prefs, new ChatFilterEntityStore(db)).save(values))
                    .isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(restore));
            if (rejectedKey.equals("theme")) assertThat(node.get("chat.show.username", null)).isEqualTo("true");
            else assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            verify(prefs, times(2)).save();
        }
    }

    @Test void nonEditorKeysAreRejectedBeforeAnyStoreIsChanged() {
        var node = new MemoryPreferences();
        var prefs = spy(new AppPreferences(node));
        try (var db = new TestDBConnector()) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var invalid = new SettingsDraft(Map.of("user.data.file", Optional.of("another.db")), draft("NORD").filters());
            assertThatThrownBy(() -> new SettingsSaveService(prefs, filters).save(invalid)).isInstanceOf(IllegalArgumentException.class);
            assertThat(node.get("user.data.file", null)).isNull();
            assertThat(filters.read().keywords()).isEmpty();
            verify(prefs, never()).save();
        }
    }

    @Test void sqlFailureBeforeTheCompanionDoesNotWriteOrFlushPreferences() {
        var node = new MemoryPreferences();
        node.put("theme", "PRIMER");
        var prefs = spy(new AppPreferences(node));
        try (var db = new TestDBConnector()) {
            db.connect();
            db.execute("create trigger reject_filter before insert on chat_keyword_filter begin select raise(abort, 'reject'); end;");
            db.commit();
            var filters = new ChatFilterEntityStore(db);
            assertThatThrownBy(() -> new SettingsSaveService(prefs, filters).save(draft("NORD"))).hasMessageContaining("reject");
            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            verify(prefs, never()).save();
        }
    }

    @Test void applyingACommittedSnapshotUpdatesAllCachedTypesWithoutWritingAgain(org.testfx.api.FxRobot robot) throws Exception {
        var node = spy(new MemoryPreferences());
        var prefs = new AppPreferences(node);
        var draft = new java.util.concurrent.atomic.AtomicReference<SettingsDraft>();
        robot.interact(() -> {
            prefs.getGeneralPreferences().getTheme();
            var chat = prefs.getChatPreferences();
            chat.getFont(); chat.isShowUserName(); chat.isShowBadges(); chat.getChatCacheSize();
            var builder = new SettingsDraft.Builder();
            builder.values(com.github.k7t3.tcv.prefs.GeneralPreferences.editorValues(com.github.k7t3.tcv.app.theme.Theme.NORD,
                    com.github.k7t3.tcv.app.theme.ThemeType.DARKER, com.github.k7t3.tcv.app.channel.MultipleChatOpenType.SEPARATED));
            builder.values(com.github.k7t3.tcv.prefs.ChatPreferences.editorValues(new com.github.k7t3.tcv.prefs.FontSettings("System", 19), false, false, 192));
            draft.set(builder.build());
        });
        try (var db = new TestDBConnector(); var worker = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var saved = worker.submit(() -> new SettingsSaveService(prefs, new ChatFilterEntityStore(db)).save(draft.get()))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            var bytes = node.getByteArray("chat.font.family", null);
            assertThat(com.github.k7t3.tcv.prefs.FontSettings.deserialize(bytes))
                    .isEqualTo(new com.github.k7t3.tcv.prefs.FontSettings("System", 19));
            clearInvocations(node);
            robot.interact(() -> {
                prefs.applyCommittedValues(saved.draft().values());
                assertThat(prefs.getGeneralPreferences().getTheme()).isEqualTo(com.github.k7t3.tcv.app.theme.Theme.NORD);
                assertThat(prefs.getChatPreferences().getFont().size()).isEqualTo(19);
                assertThat(prefs.getChatPreferences().isShowUserName()).isFalse();
                assertThat(prefs.getChatPreferences().isShowBadges()).isFalse();
                assertThat(prefs.getChatPreferences().getChatCacheSize()).isEqualTo(192);
            });
            verifyNoInteractions(node);
        }
    }
}
