package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.support.TestJvm;

import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsProfileProcessTest {
    @TempDir Path directory;

    @Test
    void savesFromDifferentDatabasesCannotFlushOverEachOthersPreferences() throws Exception {
        runSaves(false);
    }

    @Test
    void theDefaultApplicationPreferencesCoordinateDifferentDatabaseWriters() throws Exception {
        runSaves(true);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void editorAndCredentialProcessesPreserveBothCommittedValues(boolean defaults) throws Exception {
        runSaves(defaults, true);
    }

    private void runSaves(boolean defaults) throws Exception {
        runSaves(defaults, false);
    }

    private void runSaves(boolean defaults, boolean credentials) throws Exception {
        var preferenceFile = directory.resolve("shared.properties");
        var node = new FilePreferences(preferenceFile);
        node.put("theme", "PRIMER");
        node.put("chat.show.username", "true");
        node.flush();
        var firstReady = directory.resolve("first-ready");
        var secondReady = directory.resolve("second-ready");
        try (var watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            var first = startWriter("first", preferenceFile, firstReady, defaults);
            try (var firstOwner = new OwnedWriter(first)) {
                awaitFile(firstReady, watcher);
                var second = startWriter(credentials ? "credentials" : "second", preferenceFile, secondReady, defaults);
                try (var secondOwner = new OwnedWriter(second)) {
                    awaitFile(secondReady, watcher);
                    // 共有排他がなければ後続が先に終了する。共有排他があれば先行を解放してから進む。
                    if (credentials) assertThat(second.waitFor(500, TimeUnit.MILLISECONDS)).isFalse();
                    else second.waitFor(1, TimeUnit.SECONDS);
                    first.getOutputStream().write(1);
                    first.getOutputStream().flush();
                    assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
                    assertThat(second.waitFor(10, TimeUnit.SECONDS)).isTrue();
                    assertThat(first.exitValue()).withFailMessage(Files.readString(directory.resolve("first.log"))).isZero();
                    if (credentials) assertThat(second.exitValue()).withFailMessage(Files.readString(directory.resolve("credentials.log"))).isZero();
                    else {
                        assertThat(second.exitValue()).withFailMessage(Files.readString(directory.resolve("second.log"))).isZero();
                    }

                    var saved = new FilePreferences(preferenceFile);
                    assertThat(saved.get("theme", null)).isEqualTo("NORD");
                    if (credentials) {
                        assertThat(saved.get("chat.show.username", null)).isEqualTo("true");
                        var stored = new PreferencesCredentialStorage(saved, byte[]::clone, byte[]::clone);
                        assertThat(stored.loadCredentials()).extracting(value -> ((OAuth2Credential) value).getAccessToken())
                                .containsExactly("synthetic-mixed-writer");
                    } else {
                        assertThat(saved.get("chat.show.username", null)).isEqualTo("false");
                    }
                }
            }
        }
    }

    private Process startWriter(String mode, Path preferences, Path ready, boolean defaults) throws IOException {
        return TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + directory.resolve("owned-home"),
                "-Djava.util.prefs.PreferencesFactory=" + FileFactory.class.getName(),
                "-Dtcv.test.preferences.file=" + preferences,
                "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                Writer.class.getName(), mode, directory.resolve(mode + ".db").toString(), preferences.toString(),
                ready.toString(), directory.resolve("shared-settings.lock").toString(), Boolean.toString(defaults))
                .redirectErrorStream(true).redirectOutput(directory.resolve(mode + ".log").toFile()).start();
    }

    private static void awaitFile(Path file, java.nio.file.WatchService watcher) throws Exception {
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

    private static void stop(Process process) throws Exception {
        try (var output = process.getOutputStream(); var input = process.getInputStream(); var errors = process.getErrorStream()) {
            if (process.isAlive()) process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private record OwnedWriter(Process process) implements AutoCloseable {
        @Override public void close() throws Exception { stop(process); }
    }

    public static final class Writer {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("credentials")) {
                var node = new FilePreferences(Path.of(args[2]));
                var preferences = Boolean.parseBoolean(args[5]) ? new AppPreferences() : new AppPreferences(node, Path.of(args[4]));
                Files.writeString(Path.of(args[3]), "attempting");
                var storage = new PreferencesCredentialStorage(preferences.getPreferences(), byte[]::clone, byte[]::clone,
                        Path.of(args[4]).resolveSibling("credentials.lock"), preferences.settingsLockFile().orElseThrow());
                storage.saveCredentials(List.of(new OAuth2Credential("synthetic", "synthetic-mixed-writer", null,
                        "owned", "Owned", 3600, List.of(), Map.of())));
                return;
            }
            var first = args[0].equals("first");
            var node = new FilePreferences(Path.of(args[2]));
            var prepared = new java.util.concurrent.atomic.AtomicBoolean();
            var paused = new java.util.concurrent.atomic.AtomicBoolean();
            try (var db = new SQLiteDBConnector(Path.of(args[1])) {
                @Override public synchronized void prepared(String sql,
                        com.github.k7t3.tcv.database.SQLFunction<java.sql.PreparedStatement> action) {
                    super.prepared(sql, action);
                    if (sql.contains("insert into settings_recovery")) prepared.set(true);
                }
                @Override public synchronized void commit() {
                    super.commit();
                    if (first && prepared.get() && paused.compareAndSet(false, true)) {
                        try {
                            Files.writeString(Path.of(args[3]), "prepared");
                            if (System.in.read() < 0) throw new AssertionError("first writer not released");
                        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    }
                }
            }) {
                db.connect();
                if (!first) Files.writeString(Path.of(args[3]), "attempting");
                var preferences = Boolean.parseBoolean(args[5]) ? new AppPreferences() : new AppPreferences(node, Path.of(args[4]));
                new SettingsSaveService(preferences, new ChatFilterEntityStore(db)).save(
                        new SettingsDraft(Map.of(first ? "theme" : "chat.show.username", Optional.of(first ? "NORD" : "false")),
                                ChatFilterEntityStore.Changes.empty()));
            }
        }
    }

    public static final class FileFactory implements java.util.prefs.PreferencesFactory {
        private final java.util.prefs.Preferences root;
        public FileFactory() {
            try { root = new FilePreferences(Path.of(System.getProperty("tcv.test.preferences.file"))); }
            catch (BackingStoreException failure) { throw new IllegalStateException(failure); }
        }
        @Override public java.util.prefs.Preferences userRoot() { return root; }
        @Override public java.util.prefs.Preferences systemRoot() { return root; }
    }

    private static final class FilePreferences extends AbstractPreferences {
        private final Path file;
        private final Properties values = new Properties();
        FilePreferences(Path file) throws BackingStoreException { super(null, ""); this.file = file; syncSpi(); }
        private FilePreferences(FilePreferences parent, String name) {
            super(parent, name);
            file = parent.file;
            try { syncSpi(); }
            catch (BackingStoreException failure) { throw new IllegalStateException(failure); }
        }
        @Override public boolean isUserNode() { return true; }
        @Override protected void putSpi(String key, String value) { values.setProperty(key, value); }
        @Override protected String getSpi(String key) { return values.getProperty(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected String[] keysSpi() { return values.stringPropertyNames().toArray(String[]::new); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { return new FilePreferences(this, name); }
        @Override protected void removeNodeSpi() { throw new UnsupportedOperationException(); }
        @Override protected void syncSpi() throws BackingStoreException {
            try {
                values.clear();
                if (Files.exists(file)) try (var in = Files.newInputStream(file)) { values.load(in); }
            } catch (IOException failure) { throw new BackingStoreException(failure); }
        }
        @Override protected void flushSpi() throws BackingStoreException {
            try (var out = Files.newOutputStream(file)) { values.store(out, "synthetic settings"); }
            catch (IOException failure) { throw new BackingStoreException(failure); }
        }
    }
}
