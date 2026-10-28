package com.github.k7t3.tcv.app.user;

import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatUserFilterEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDataFileTest {

    @TempDir
    Path directory;

    @Test
    void reopeningDatabaseKeepsExistingServicesUsable() throws Exception {
        var file = new UserDataFile(directory.resolve("source.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        try {
            var service = new ChatUserFilterEntityService(file.getConnector());
            var filter = new ChatUserFilterEntity("123", "user", "comment");
            service.save(filter);
            file.closeDatabase();

            file.connectDatabase(DatabaseVersion.latest());

            assertThat(service.retrieveAll()).containsExactly(filter);
        } finally {
            file.closeDatabase();
        }
    }

    @Test
    void opensRelativeFileNameWithoutParent() throws Exception {
        // 親を持たないパスを検証するため、このケースだけ作業ディレクトリを使う。
        var relative = Path.of("tcv-test-" + UUID.randomUUID() + ".db");
        var file = new UserDataFile(relative);
        try {
            file.connectDatabase(DatabaseVersion.latest());

            var filter = new ChatUserFilterEntity("123", "user", "comment");
            var service = new ChatUserFilterEntityService(file.getConnector());
            service.save(filter);
            assertThat(service.retrieveAll()).containsExactly(filter);
        } finally {
            file.closeDatabase();
            Files.deleteIfExists(relative);
        }
    }

    @Test
    void movingUnopenedFileUpdatesPath() throws Exception {
        var source = Files.writeString(directory.resolve("source.db"), "unopened file");
        var destination = directory.resolve("destination.db");
        var file = new UserDataFile(source);

        file.fileMove(destination);

        assertThat(file.getFilePath()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("unopened file");
        assertThat(Files.exists(source)).isFalse();
    }

    @Test
    void movingToSameFileKeepsDatabaseFile() throws Exception {
        var source = directory.resolve("source.db");
        var child = Files.createDirectory(directory.resolve("child"));
        var file = new UserDataFile(source);
        file.connectDatabase(DatabaseVersion.EMPTY);
        try {
            var service = new ChatUserFilterEntityService(file.getConnector());
            var filter = new ChatUserFilterEntity("123", "user", "comment");
            service.save(filter);

            file.fileMove(child.resolve("..").resolve("source.db"));

            assertThat(Files.exists(source)).isTrue();
            assertThat(service.retrieveAll()).containsExactly(filter);
        } finally {
            file.closeDatabase();
        }
    }

    @Test
    void failedMoveKeepsOriginalConnectionAndDestinationContents() throws Exception {
        var source = directory.resolve("source.db");
        var destination = Files.writeString(directory.resolve("existing.db"), "keep destination");
        var file = new UserDataFile(source);
        file.connectDatabase(DatabaseVersion.EMPTY);
        try {
            var service = new ChatUserFilterEntityService(file.getConnector());
            var filter = new ChatUserFilterEntity("123", "user", "comment");
            service.save(filter);

            assertThatThrownBy(() -> file.fileMove(destination)).isInstanceOf(FileAlreadyExistsException.class);

            assertThat(file.getFilePath()).isEqualTo(source);
            assertThat(Files.readString(destination)).isEqualTo("keep destination");
            assertThat(service.retrieveAll()).containsExactly(filter);
        } finally {
            file.closeDatabase();
        }
    }

    @Test
    void movesOpenDatabaseAndKeepsExistingServicesUsable() throws Exception {
        var source = directory.resolve("source.db");
        var destination = directory.resolve("destination.db");
        var file = new UserDataFile(source);
        file.connectDatabase(DatabaseVersion.EMPTY);
        try {
            var service = new ChatUserFilterEntityService(file.getConnector());
            var original = new ChatUserFilterEntity("123", "original", "comment");
            service.save(original);

            file.fileMove(destination);

            assertThat(file.getFilePath()).isEqualTo(destination);
            assertThat(Files.exists(source)).isFalse();
            assertThat(service.retrieveAll()).containsExactly(original);
            var added = new ChatUserFilterEntity("456", "added", "comment");
            service.save(added);
            file.closeDatabase();
            file.connectDatabase(DatabaseVersion.latest());
            assertThat(new ChatUserFilterEntityService(file.getConnector()).retrieveAll())
                    .containsExactlyInAnyOrder(original, added);
        } finally {
            file.closeDatabase();
        }
    }
}
