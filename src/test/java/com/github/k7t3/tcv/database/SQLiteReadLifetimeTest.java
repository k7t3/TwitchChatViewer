package com.github.k7t3.tcv.database;

import com.github.k7t3.tcv.support.TestJvm;

import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatUserFilterEntityService;
import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import com.github.k7t3.tcv.entity.service.ChatKeywordFilterEntityService;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteReadLifetimeTest {
    @TempDir Path directory;

    @Test
    void aSeparateProcessCanCommitAfterTheApplicationHasReadItsFilters() throws Exception {
        var path = directory.resolve("process.db");
        var log = directory.resolve("writer.log");
        try (var reader = new SQLiteDBConnector(path)) {
            reader.connect();
            var filters = new ChatFilterEntityStore(reader);
            assertThat(filters.read().users()).isEmpty();
            var process = TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"),
                    WriterProcess.class.getName(), path.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                process.getOutputStream().close();
                assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();

                assertThat(filters.read().users()).containsExactly(new ChatUserFilterEntity("123", "user", "comment"));
            } finally {
                if (process.isAlive()) process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
                process.getInputStream().close();
                process.getErrorStream().close();
            }
        }
    }

    public static final class WriterProcess {
        public static void main(String[] arguments) {
            try (var writer = new SQLiteDBConnector(Path.of(arguments[0]))) {
                writer.connect();
                writer.execute("pragma busy_timeout = 100;");
                new ChatUserFilterEntityService(writer).save(new ChatUserFilterEntity("123", "user", "comment"));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFailedReadReleasesItsLockAndPreservesTheOriginalFailureAndCommitMode(boolean autoCommit) {
        var path = directory.resolve("failed-read.db");
        var failure = new IllegalArgumentException("mapping failed");
        var row = new ChatUserFilterEntity("123", "other", "committed");
        var pending = new ChatUserFilterEntity("456", "reader", "after failure");
        try (var reader = new SQLiteDBConnector(path); var other = new SQLiteDBConnector(path)) {
            reader.connect();
            other.connect();
            reader.setAutoCommit(autoCommit);
            other.execute("pragma busy_timeout = 100;");
            other.commit();

            assertThatThrownBy(() -> reader.readTransaction(() ->
                    reader.query("select * from chat_user_filter;", rs -> { throw failure; })))
                    .isSameAs(failure);

            new ChatUserFilterEntityService(other).save(row);
            assertThat(new ChatUserFilterEntityService(reader).retrieveAll()).containsExactly(row);
            reader.execute("insert into chat_user_filter values('456', 'reader', 'after failure');");
            assertThat(new ChatUserFilterEntityService(other).retrieveAll())
                    .containsExactlyInAnyOrderElementsOf(autoCommit ? List.of(row, pending) : List.of(row));
            if (!autoCommit) reader.rollback();
        }
    }

    @Test
    void aCompoundReadInsideAnUpdateCannotCommitBeforeTheOuterRollback() {
        var path = directory.resolve("outer.db");
        var failure = new IllegalStateException("outer update failed");
        try (var writer = new SQLiteDBConnector(path); var observer = new SQLiteDBConnector(path)) {
            writer.connect();
            observer.connect();

            assertThatThrownBy(() -> writer.transaction(() -> {
                writer.execute("insert into chat_user_filter values('123', 'user', 'comment');");
                assertThat(new ChatFilterEntityStore(writer).read().users())
                        .containsExactly(new ChatUserFilterEntity("123", "user", "comment"));
                throw failure;
            })).isSameAs(failure);

            assertThat(new ChatFilterEntityStore(observer).read().users()).isEmpty();
        }
    }

    @Test
    void anEntityReadPreservesAutoCommitMode() {
        var path = directory.resolve("auto.db");
        var row = new ChatUserFilterEntity("123", "user", "comment");
        try (var writer = new SQLiteDBConnector(path); var observer = new SQLiteDBConnector(path)) {
            writer.connect();
            observer.connect();
            writer.setAutoCommit(true);

            assertThat(new ChatUserFilterEntityService(writer).retrieveAll()).isEmpty();

            writer.execute("insert into chat_user_filter values('123', 'user', 'comment');");
            assertThat(new ChatUserFilterEntityService(observer).retrieveAll()).containsExactly(row);
        }
    }

    @Test
    void anEntityReadDoesNotCommitTheCallersPendingManualWrite() {
        var path = directory.resolve("manual.db");
        var row = new ChatUserFilterEntity("123", "user", "comment");
        try (var writer = new SQLiteDBConnector(path); var observer = new SQLiteDBConnector(path)) {
            writer.connect();
            observer.connect();
            writer.execute("insert into chat_user_filter values('123', 'user', 'comment');");

            assertThat(new ChatUserFilterEntityService(writer).retrieveAll()).containsExactly(row);

            assertThat(new ChatUserFilterEntityService(observer).retrieveAll()).isEmpty();
            writer.rollback();
            assertThat(new ChatUserFilterEntityService(writer).retrieveAll()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "groups", "window"})
    void everyPublicEntityReaderReleasesItsSnapshotBeforeReturning(String kind) {
        var path = directory.resolve("reader.db");
        var row = new ChatUserFilterEntity("123", "user", "comment");
        try (var writer = new SQLiteDBConnector(path); var reader = new SQLiteDBConnector(path)) {
            writer.connect();
            reader.connect();
            writer.execute("pragma busy_timeout = 100;");
            switch (kind) {
                case "keyword" -> assertThat(new ChatKeywordFilterEntityService(reader).retrieveAll()).isEmpty();
                case "groups" -> assertThat(new ChannelGroupEntityService(reader).retrieveAll()).isEmpty();
                case "window" -> assertThat(new WindowBoundsEntityService(reader).get("main")).isEqualTo(WindowBoundsEntity.DEFAULT);
                default -> throw new AssertionError("unexpected reader");
            }

            new ChatUserFilterEntityService(writer).save(row);

            assertThat(new ChatUserFilterEntityService(reader).retrieveAll()).containsExactly(row);
        }
    }

    @Test
    void aCompletedEntityReadDoesNotBlockAnotherWriterOrKeepTheOldSnapshot() {
        var path = directory.resolve("read.db");
        var row = new ChatUserFilterEntity("123", "user", "comment");
        try (var writer = new SQLiteDBConnector(path); var reader = new SQLiteDBConnector(path)) {
            writer.connect();
            reader.connect();
            writer.execute("pragma busy_timeout = 100;");
            var reads = new ChatUserFilterEntityService(reader);
            assertThat(reads.retrieveAll()).isEmpty();

            new ChatUserFilterEntityService(writer).save(row);

            assertThat(reads.retrieveAll()).containsExactly(row);
        }
    }

    @Test
    void openingAnExistingDatabaseDoesNotKeepALockThatBlocksAnotherConnectionsCommit() {
        var path = directory.resolve("shared.db");
        var row = new ChatUserFilterEntity("123", "user", "comment");
        try (var writer = new SQLiteDBConnector(path); var reader = new SQLiteDBConnector(path)) {
            writer.connect();
            reader.connect();
            writer.execute("pragma busy_timeout = 100;");

            new ChatUserFilterEntityService(writer).save(row);

            assertThat(new ChatUserFilterEntityService(reader).retrieveAll()).containsExactly(row);
        }
    }
}
