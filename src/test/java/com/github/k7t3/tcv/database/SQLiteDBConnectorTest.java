package com.github.k7t3.tcv.database;

import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatUserFilterEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteDBConnectorTest {

    @TempDir
    Path directory;

    @Test
    void fileDatabaseEnablesRecoveryJournalAndFullSynchronization() {
        try (var connector = new SQLiteDBConnector(directory.resolve("durable.db"))) {
            connector.connect();
            var journalModes = new ArrayList<String>();
            var syncLevels = new ArrayList<Integer>();

            connector.query("pragma journal_mode;", rs -> {
                while (rs.next()) journalModes.add(rs.getString(1));
            });
            connector.query("pragma synchronous;", rs -> {
                while (rs.next()) syncLevels.add(rs.getInt(1));
            });

            assertThat(journalModes).containsExactly("delete");
            assertThat(syncLevels).containsExactly(2);
        }
    }

    @Test
    void initializesExistingEmptyFile() throws Exception {
        var file = Files.createFile(directory.resolve("empty.db"));
        var filter = new ChatUserFilterEntity("123", "user", "comment");
        try (var connector = new SQLiteDBConnector(file)) {
            connector.connect();

            var service = new ChatUserFilterEntityService(connector);
            service.save(filter);

            assertThat(service.retrieveAll()).containsExactly(filter);
        }
    }

    @Test
    void failedSchemaCreationRollsBackAndReleasesConnection() throws Exception {
        var file = directory.resolve("failed.db");
        var failure = new IllegalArgumentException("schema creation failed");
        try (var connector = new SQLiteDBConnector(file, db -> {
            db.execute("create table partial_schema (id integer);");
            throw failure;
        })) {
            assertThatThrownBy(connector::connect).isSameAs(failure);

            assertThat(connector.isConnected()).isFalse();
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select name from sqlite_master where type = 'table';")) {
            assertThat(rows.next()).isFalse();
        }
    }

    @Test
    void initializesNewFileAndKeepsCommittedDataOnReopen() {
        var file = directory.resolve("userdata.db");
        var filter = new ChatUserFilterEntity("123", "user", "comment");
        try (var connector = new SQLiteDBConnector(file)) {
            connector.connect();
            new ChatUserFilterEntityService(connector).save(filter);
        }

        try (var connector = new SQLiteDBConnector(file)) {
            connector.connect();
            assertThat(new ChatUserFilterEntityService(connector).retrieveAll()).containsExactly(filter);
        }
    }
}
