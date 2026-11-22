package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.support.TestJvm;

import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.PreferenceValuesCodec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;

class SettingsCrashRecoveryTest {
    @TempDir Path directory;

    @Test
    void aProcessStoppedDuringCompensationCanRecoverBothDatabasesInANewJvm() throws Exception {
        var first = directory.resolve("first.db");
        var second = directory.resolve("second.db");
        var values = directory.resolve("preferences.properties");
        var node = new FilePreferences(values);
        node.put("theme", "NORD");
        node.put("credential", "synthetic-preserved");
        var operation = java.util.UUID.randomUUID();
        com.github.k7t3.tcv.prefs.PreferenceProjection.beginProjection(node, operation);
        com.github.k7t3.tcv.prefs.PreferenceProjection.claim(node, operation, Map.of("theme", Optional.of("NORD")));
        node.flush();
        try (var db = new SQLiteDBConnector(first)) {
            db.connect();
            var journal = new ChatFilterEntityStore(db).settingsJournal();
            journal.exclusive(() -> {
                journal.prepare(new com.github.k7t3.tcv.entity.service.SettingsJournal.Entry(operation,
                        node.absolutePath(), node.isUserNode(),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("PRIMER"))),
                        PreferenceValuesCodec.encode(Map.of("theme", Optional.of("NORD"))), false,
                        Optional.of(PreferenceValuesCodec.encode(Map.of()))));
                return null;
            });
        }

        runRecoveryChainProcess(first, second, values, "stop", 26);
        assertThat(new FilePreferences(values).get("theme", null)).isEqualTo("CUPERTINO");
        runRecoveryChainProcess(first, second, values, "recover", 0);

        var recovered = new FilePreferences(values);
        assertThat(recovered.get("theme", null)).isEqualTo("PRIMER");
        assertThat(recovered.get("credential", null)).isEqualTo("synthetic-preserved");
        assertThat(recovered.keys()).containsExactlyInAnyOrder("theme", "credential");
        for (var path : List.of(first, second)) {
            try (var db = new SQLiteDBConnector(path)) {
                db.connect();
                var journal = new ChatFilterEntityStore(db).settingsJournal();
                assertThat(journal.exclusive(journal::pending)).isEmpty();
            }
        }
    }

    private void runRecoveryChainProcess(Path first, Path second, Path values, String mode, int expectedExit) throws Exception {
        var log = directory.resolve("chain-" + mode + ".log");
        var process = TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                RecoveryChainProcess.class.getName(), first.toString(), second.toString(), values.toString(), mode)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            process.getOutputStream().close();
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isEqualTo(expectedExit);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    public static final class RecoveryChainProcess {
        public static void main(String[] arguments) throws Exception {
            var node = new FilePreferences(Path.of(arguments[2]));
            if (arguments[3].equals("stop")) {
                node.afterFlush = () -> { throw new IllegalStateException("synthetic flush acknowledgement failed"); };
                node.beforePut = (key, value) -> {
                    if (key.equals("theme") && value.equals("NORD")) Runtime.getRuntime().halt(26);
                };
                try (var db = new SQLiteDBConnector(Path.of(arguments[1]))) {
                    db.connect();
                    new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(db)).save(
                            new SettingsDraft(Map.of("theme", Optional.of("CUPERTINO")), ChatFilterEntityStore.Changes.empty()));
                    throw new AssertionError("writer did not stop during compensation");
                }
            }
            try (var first = new SQLiteDBConnector(Path.of(arguments[0]));
                 var second = new SQLiteDBConnector(Path.of(arguments[1]))) {
                first.connect();
                second.connect();
                var preferences = new AppPreferences(node);
                new SettingsSaveService(preferences, new ChatFilterEntityStore(second)).recover();
                new SettingsSaveService(preferences, new ChatFilterEntityStore(first)).recover();
            }
        }
    }

    @Test
    void anUncommittedMarkerOnAnInvalidConnectionCannotBeMistakenForACommittedSave() throws Exception {
        var path = directory.resolve("invalid-connection.db");
        var preferencesFile = directory.resolve("invalid-connection-preferences.properties");
        var node = new FilePreferences(preferencesFile);
        node.put("theme", "PRIMER");
        node.flush();
        var prefs = spy(new AppPreferences(node));
        var flushed = new java.util.concurrent.atomic.AtomicBoolean();
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var failure = new IllegalStateException("commit failed before publication");
        var rollback = new IllegalStateException("rollback failed");
        var close = new IllegalStateException("close failed");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            flushed.set(true);
            return null;
        }).when(prefs).save();
        try (var db = spy(new SQLiteDBConnector(path))) {
            db.connect();
            doAnswer(invocation -> {
                if (flushed.get() && failOnce.getAndSet(false)) throw failure;
                return invocation.callRealMethod();
            }).when(db).commit();
            doThrow(rollback).when(db).rollback();
            doThrow(close).when(db).close();
            try {
                var draft = new SettingsDraft(Map.of("theme", Optional.of("NORD")),
                        new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(0, 0, "blocked")),
                                Set.of(), List.of(), Set.of()));

                assertThatThrownBy(() -> new SettingsSaveService(prefs, new ChatFilterEntityStore(db)).save(draft))
                        .isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).contains(rollback, close));

                assertThat(db.isConnected()).isFalse();
            } finally { doCallRealMethod().when(db).close(); }
        }
        try (var fresh = new SQLiteDBConnector(path)) {
            fresh.connect();
            var restored = new FilePreferences(preferencesFile);
            var filters = new ChatFilterEntityStore(fresh);

            new SettingsSaveService(new AppPreferences(restored), filters).recover();

            assertThat(restored.get("theme", null)).isEqualTo("PRIMER");
            assertThat(filters.read().keywords()).isEmpty();
        }
    }

    @Test
    void recoveryCannotProjectIntoAnotherPreferencesTree() throws Exception {
        var path = directory.resolve("other-tree.db");
        var preferencesFile = directory.resolve("other-tree-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var unrelated = new com.github.k7t3.tcv.support.MemoryPreferences();
            unrelated.put("theme", "unrelated-tree-value");

            assertThatThrownBy(() -> new SettingsSaveService(new AppPreferences(unrelated), new ChatFilterEntityStore(db)).recover())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("another preferences node");

            assertThat(unrelated.get("theme", null)).isEqualTo("unrelated-tree-value");
            var matching = new FilePreferences(preferencesFile);
            new SettingsSaveService(new AppPreferences(matching), new ChatFilterEntityStore(db)).recover();
            assertThat(matching.get("theme", null)).isEqualTo("PRIMER");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-version", "truncated", "negative-count", "trailing-bytes", "foreign-key", "keys-mismatch"})
    void invalidRecoveryDataCannotPartiallyProjectOrDiscardTheRecoveryRecord(String kind) throws Exception {
        var path = directory.resolve("invalid-record.db");
        var preferencesFile = directory.resolve("invalid-record-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.put("credential", "synthetic-untouched");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            var journal = filters.settingsJournal();
            var original = journal.exclusive(() -> journal.pending().orElseThrow());
            var badAfter = switch (kind) {
                case "unknown-version" -> new byte[] {0, 0, 0, 2, 0, 0, 0, 0};
                case "truncated" -> new byte[] {0};
                case "negative-count" -> new byte[] {0, 0, 0, 1, -1, -1, -1, -1};
                case "foreign-key" -> PreferenceValuesCodec.encode(Map.of("credential", Optional.of("synthetic-attempt")));
                case "keys-mismatch" -> PreferenceValuesCodec.encode(Map.of("chat.show.username", Optional.of("false")));
                case "trailing-bytes" -> {
                    var valid = original.after();
                    yield java.util.Arrays.copyOf(valid, valid.length + 1);
                }
                default -> throw new AssertionError("unknown corruption fixture");
            };
            var badBefore = kind.equals("foreign-key") ? badAfter : original.before();
            replacePayload(db, badBefore, badAfter);
            var node = new FilePreferences(preferencesFile);
            var service = new SettingsSaveService(new AppPreferences(node), filters);

            assertThatThrownBy(service::recover).isInstanceOf(IllegalArgumentException.class);

            assertThat(node.get("theme", null)).isEqualTo("NORD");
            assertThat(node.get("credential", null)).isEqualTo("synthetic-untouched");
            replacePayload(db, original.before(), original.after());
            service.recover();
            assertThat(new FilePreferences(preferencesFile).get("theme", null)).isEqualTo("PRIMER");
        }
    }

    private static void replacePayload(com.github.k7t3.tcv.database.DBConnector db, byte[] before, byte[] after) {
        db.transaction(() -> db.prepared("update settings_recovery set before_values = ?, after_values = ? where slot = 1;", statement -> {
            statement.setBytes(1, before);
            statement.setBytes(2, after);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }));
    }

    @Test
    void constructingTheSettingsServiceDoesNotOpenStorageOrCreateALockFile() throws Exception {
        try (var db = new SQLiteDBConnector(directory.resolve("unopened.db"))) {
            assertThatCode(() -> new SettingsSaveService(new AppPreferences(new com.github.k7t3.tcv.support.MemoryPreferences()),
                    new ChatFilterEntityStore(db))).doesNotThrowAnyException();
            assertThat(db.isConnected()).isFalse();
            try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
        }
    }

    @Test
    void theApplicationOwnerRecoversThePendingSettingsBeforeTheThemeIsRead() throws Exception {
        var path = directory.resolve("owner.db");
        var preferencesFile = directory.resolve("owner-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        var preferences = new AppPreferences(new FilePreferences(preferencesFile));
        var data = new com.github.k7t3.tcv.app.user.UserDataFile(path);
        var errors = new java.util.ArrayList<Throwable>();
        try (var helper = new com.github.k7t3.tcv.app.core.AppHelper(errors::add)) {
            data.connectDatabase(com.github.k7t3.tcv.database.DatabaseVersion.EMPTY);
            helper.setUserDataFile(data);

            helper.recoverSettings(preferences);

            assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(com.github.k7t3.tcv.app.theme.Theme.PRIMER);
            assertThat(helper.getChatFilterStore().read().revision()).isZero();
            assertThat(errors).isEmpty();
        } finally { data.closeDatabase(); }
        assertThat(data.getConnector().isConnected()).isFalse();
    }

    @Test
    void aFailedRecoveryFlushKeepsTheRecordForTheNextApplicationLifetime() throws Exception {
        var path = directory.resolve("retry.db");
        var preferencesFile = directory.resolve("retry-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        var node = new FilePreferences(preferencesFile);
        var failure = new IllegalStateException("recovery flush failed");
        node.afterFlush = () -> { throw failure; };
        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var service = new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(db));

            assertThatThrownBy(service::recover).isSameAs(failure);
        }
        var stale = new FilePreferences(preferencesFile);
        stale.put("theme", "NORD");
        stale.flush();
        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var fresh = new FilePreferences(preferencesFile);

            new SettingsSaveService(new AppPreferences(fresh), new ChatFilterEntityStore(db)).recover();

            assertThat(new FilePreferences(preferencesFile).get("theme", null)).isEqualTo("PRIMER");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid-old-theme"})
    void recoveryPreservesTheExactOldRawValueIncludingAnAbsentOrInvalidValue(String raw) throws Exception {
        var path = directory.resolve("raw.db");
        var preferencesFile = directory.resolve("raw-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        if (raw != null) initial.put("theme", raw);
        initial.put("credential", "synthetic-untouched");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var fresh = new FilePreferences(preferencesFile);

            new SettingsSaveService(new AppPreferences(fresh), new ChatFilterEntityStore(db)).recover();

            var persisted = new FilePreferences(preferencesFile);
            assertThat(persisted.get("theme", null)).isEqualTo(raw);
            assertThat(persisted.get("credential", null)).isEqualTo("synthetic-untouched");
        }
    }

    @Test
    void recoveryBeforeBuildingViewsDoesNotRequireSubmittingANewSave() throws Exception {
        var path = directory.resolve("startup.db");
        var preferencesFile = directory.resolve("startup-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        var node = new FilePreferences(preferencesFile);
        var preferences = new AppPreferences(node);

        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            new SettingsSaveService(preferences, filters).recover();

            assertThat(preferences.getGeneralPreferences().getTheme()).isEqualTo(com.github.k7t3.tcv.app.theme.Theme.PRIMER);
            assertThat(new FilePreferences(preferencesFile).get("theme", null)).isEqualTo("PRIMER");
            assertThat(filters.read().keywords()).isEmpty();
            assertThat(filters.read().revision()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"paused-commit", "paused-prepare"})
    void anotherProcessCannotRecoverThePreferencesOfAWriterThatHasNotFinished(String boundary) throws Exception {
        var path = directory.resolve("shared.db");
        var preferencesFile = directory.resolve("shared-preferences.properties");
        var ready = directory.resolve("writer-ready");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.flush();
        try (var db = new SQLiteDBConnector(path);
             var workers = Executors.newVirtualThreadPerTaskExecutor();
             var watcher = directory.getFileSystem().newWatchService()) {
            db.connect();
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            var process = TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                    StoppedWriter.class.getName(), path.toString(), preferencesFile.toString(), boundary, ready.toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("paused-writer.log").toFile()).start();
            try {
                awaitFile(ready, watcher);
                var node = new FilePreferences(preferencesFile);
                var interference = new IllegalStateException("another live writer was compensated");
                node.afterFlush = () -> {
                    if (node.get("theme", "").equals("PRIMER")) throw interference;
                };
                var service = new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(db));
                var started = new java.util.concurrent.CountDownLatch(1);
                var saving = workers.submit(() -> {
                    started.countDown();
                    return service.save(new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty()));
                });
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> saving.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    assertThat(new FilePreferences(preferencesFile).get("theme", null))
                            .isEqualTo(boundary.equals("paused-commit") ? "NORD" : "PRIMER");
                } finally {
                    process.getOutputStream().write(1);
                    process.getOutputStream().close();
                }
                assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).withFailMessage(Files.readString(directory.resolve("paused-writer.log"))).isZero();
                assertThat(saving.get(10, TimeUnit.SECONDS).filters().keywords())
                        .extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
                assertThat(new FilePreferences(preferencesFile).get("theme", null)).isEqualTo("NORD");
            } finally {
                if (process.isAlive()) process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
                process.getOutputStream().close();
                process.getInputStream().close();
                process.getErrorStream().close();
            }
        }
    }

    private static void awaitFile(Path file, WatchService watcher) throws Exception {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(file)) {
            var remaining = deadline - System.nanoTime();
            assertThat(remaining).isPositive();
            var event = watcher.poll(remaining, TimeUnit.NANOSECONDS);
            assertThat(event).isNotNull();
            event.pollEvents();
            event.reset();
        }
    }

    @Test
    void aCommittedSqlChangeProjectsTheNewPreferencesAfterAProcessStop() throws Exception {
        var path = directory.resolve("settings.db");
        var preferencesFile = directory.resolve("synthetic-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "after-commit", 24);
        var node = new FilePreferences(preferencesFile);
        node.put("theme", "PRIMER");
        node.flush();

        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            new SettingsSaveService(new AppPreferences(node), filters)
                    .save(new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty()));

            assertThat(node.get("theme", null)).isEqualTo("NORD");
            assertThat(new FilePreferences(preferencesFile).get("theme", null)).isEqualTo("NORD");
            assertThat(filters.read().keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("blocked");
        }
    }

    @Test
    void theNextSaveRestoresPreferencesWhenThePreviousProcessStoppedBeforeSqlCommit() throws Exception {
        var path = directory.resolve("settings.db");
        var preferencesFile = directory.resolve("synthetic-preferences.properties");
        var initial = new FilePreferences(preferencesFile);
        initial.put("theme", "PRIMER");
        initial.put("credential", "synthetic-untouched");
        initial.flush();
        runStoppedWriter(path, preferencesFile, "before-commit", 23);
        var node = new FilePreferences(preferencesFile);
        assertThat(node.get("theme", null)).isEqualTo("NORD");

        try (var db = new SQLiteDBConnector(path)) {
            db.connect();
            var filters = new ChatFilterEntityStore(db);
            new SettingsSaveService(new AppPreferences(node), filters)
                    .save(new SettingsDraft(Map.of(), ChatFilterEntityStore.Changes.empty()));

            assertThat(node.get("theme", null)).isEqualTo("PRIMER");
            assertThat(new FilePreferences(preferencesFile).get("theme", null)).isEqualTo("PRIMER");
            assertThat(node.get("credential", null)).isEqualTo("synthetic-untouched");
            assertThat(filters.read().keywords()).isEmpty();
        }
    }

    private void runStoppedWriter(Path database, Path preferencesFile, String mode, int expectedExit) throws Exception {
        var log = directory.resolve("writer.log");
        var process = TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                StoppedWriter.class.getName(), database.toString(), preferencesFile.toString(), mode)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            process.getOutputStream().close();
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isEqualTo(expectedExit);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    public static final class StoppedWriter {
        public static void main(String[] arguments) throws Exception {
            var node = new FilePreferences(Path.of(arguments[1]));
            var flushed = new java.util.concurrent.atomic.AtomicBoolean();
            var afterCommit = arguments[2].equals("after-commit");
            var pausedCommit = arguments[2].equals("paused-commit");
            var pausedPrepare = arguments[2].equals("paused-prepare");
            var prepared = new java.util.concurrent.atomic.AtomicBoolean();
            var pauseOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
            Runnable pause = () -> {
                if (!pauseOnce.getAndSet(false)) return;
                try {
                    Files.writeString(Path.of(arguments[3]), "ready");
                    if (System.in.read() < 0) throw new IllegalStateException("writer was not released");
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            };
            node.afterFlush = afterCommit ? () -> flushed.set(true) : pausedCommit ? pause
                    : pausedPrepare ? () -> {} : () -> Runtime.getRuntime().halt(23);
            try (var db = new SQLiteDBConnector(Path.of(arguments[0])) {
                @Override public synchronized void prepared(String sql,
                        com.github.k7t3.tcv.database.SQLFunction<java.sql.PreparedStatement> action) {
                    super.prepared(sql, action);
                    if (sql.contains("insert into settings_recovery")) prepared.set(true);
                }
                @Override public synchronized void commit() {
                    super.commit();
                    if (pausedPrepare && prepared.getAndSet(false)) pause.run();
                    if (afterCommit && flushed.get()) Runtime.getRuntime().halt(24);
                }
            }) {
                db.connect();
                var draft = new SettingsDraft(Map.of("theme", Optional.of("NORD")),
                        new ChatFilterEntityStore.Changes(List.of(new ChatKeywordFilterEntity(0, 0, "blocked")),
                                Set.of(), List.of(), Set.of()));
                new SettingsSaveService(new AppPreferences(node), new ChatFilterEntityStore(db)).save(draft);
                if (!pausedCommit && !pausedPrepare) throw new AssertionError("writer did not stop after the forced preference flush");
            }
        }
    }

    private static final class FilePreferences extends AbstractPreferences {
        private final Properties values = new Properties();
        private final Path path;
        private Runnable afterFlush = () -> {};
        private java.util.function.BiConsumer<String, String> beforePut = (key, value) -> {};

        private FilePreferences(Path path) throws BackingStoreException {
            super(null, "");
            this.path = path;
            syncSpi();
        }

        @Override public boolean isUserNode() { return true; }

        @Override protected void putSpi(String key, String value) {
            beforePut.accept(key, value);
            values.setProperty(key, value);
        }
        @Override protected String getSpi(String key) { return values.getProperty(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { throw new UnsupportedOperationException(); }
        @Override protected String[] keysSpi() { return values.stringPropertyNames().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException(); }

        @Override protected void syncSpi() throws BackingStoreException {
            values.clear();
            try {
                if (Files.exists(path)) try (var input = Files.newInputStream(path)) { values.load(input); }
            } catch (java.io.IOException failure) { throw new BackingStoreException(failure); }
        }

        @Override protected void flushSpi() throws BackingStoreException {
            try {
                var bytes = new ByteArrayOutputStream();
                values.store(bytes, "synthetic settings recovery fixture");
                try (var output = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    var buffer = ByteBuffer.wrap(bytes.toByteArray());
                    while (buffer.hasRemaining()) output.write(buffer);
                    output.force(true);
                }
            } catch (java.io.IOException failure) { throw new BackingStoreException(failure); }
            afterFlush.run();
        }
    }
}
