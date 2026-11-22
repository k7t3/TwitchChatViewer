package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.prefs.PreferenceValuesCodec;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsJournalPersistenceTest {
    @Test
    void aHistoryInsertFailureCannotPublishOnlyTheRecoveryRecord() {
        try (var db = new TestDBConnector()) {
            db.connect();
            var journal = new ChatFilterEntityStore(db).settingsJournal();
            var entry = entry();
            journal.exclusive(() -> {
                db.transaction(() -> db.execute("""
                        create trigger reject_history before insert on settings_recovery_owners
                        begin select raise(abort, 'reject history'); end;
                        """));
                return null;
            });

            assertThatThrownBy(() -> journal.exclusive(() -> { journal.prepare(entry); return null; }))
                    .hasMessageContaining("reject history");

            assertThat(journal.exclusive(journal::pending)).isEmpty();
            journal.exclusive(() -> {
                db.transaction(() -> db.execute("drop trigger reject_history;"));
                journal.prepare(entry);
                return null;
            });
            var saved = journal.exclusive(() -> journal.pending().orElseThrow());
            assertThat(saved.operation()).isEqualTo(entry.operation());
            assertThat(saved.previousOwners().orElseThrow()).isEqualTo(entry.previousOwners().orElseThrow());
        }
    }

    @Test
    void aHistoryCleanupFailureKeepsTheRecoveryRecordAndHistoryTogether() {
        try (var db = new TestDBConnector()) {
            db.connect();
            var journal = new ChatFilterEntityStore(db).settingsJournal();
            var entry = entry();
            journal.exclusive(() -> {
                journal.prepare(entry);
                db.transaction(() -> {
                    journal.markCommitted(entry.operation());
                    db.execute("""
                            create trigger reject_cleanup before delete on settings_recovery_owners
                            begin select raise(abort, 'reject history cleanup'); end;
                            """);
                });
                return null;
            });

            assertThatThrownBy(() -> journal.exclusive(() -> { journal.clear(entry.operation()); return null; }))
                    .hasMessageContaining("reject history cleanup");

            var retained = journal.exclusive(() -> journal.pending().orElseThrow());
            assertThat(retained.committed()).isTrue();
            assertThat(retained.previousOwners().orElseThrow()).isEqualTo(entry.previousOwners().orElseThrow());
            journal.exclusive(() -> {
                db.transaction(() -> db.execute("drop trigger reject_cleanup;"));
                journal.clear(entry.operation());
                return null;
            });
            assertThat(journal.exclusive(journal::pending)).isEmpty();
            db.readTransaction(() -> db.query("select count(*) from settings_recovery_owners;", rows -> {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }));
        }
    }

    private static SettingsJournal.Entry entry() {
        return new SettingsJournal.Entry(UUID.fromString("00000000-0000-0000-0000-000000000001"), "/", false,
                PreferenceValuesCodec.encode(Map.of("theme", Optional.of("PRIMER"))),
                PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))), false,
                Optional.of(PreferenceValuesCodec.encode(Map.of())));
    }
}
