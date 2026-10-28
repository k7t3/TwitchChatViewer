package com.github.k7t3.tcv.database;

import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatUserFilterEntityService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doCallRealMethod;

class SQLiteRollbackFailureTest {
    @TempDir Path directory;

    @Test
    void evenAFailedPhysicalCloseCannotMakeTheUnknownTransactionUsableAgain() {
        var path = directory.resolve("failed-close.db");
        var committed = new ChatUserFilterEntity("123", "original", "committed");
        var failure = new IllegalStateException("update failed");
        var rollback = new IllegalStateException("rollback failed");
        var close = new IllegalStateException("close failed");
        try (var db = spy(new SQLiteDBConnector(path))) {
            db.connect();
            new ChatUserFilterEntityService(db).save(committed);
            doThrow(rollback).when(db).rollback();
            doThrow(close).when(db).close();
            try {
                assertThatThrownBy(() -> db.transaction(() -> {
                    db.execute("insert into chat_user_filter values('456', 'unfinished', 'must not be committed');");
                    throw failure;
                })).isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(rollback, close));

                var readFailure = catchThrowable(() -> new ChatUserFilterEntityService(db).retrieveAll());
                try (var observer = new SQLiteDBConnector(path)) {
                    observer.connect();
                    assertThat(new ChatUserFilterEntityService(observer).retrieveAll()).containsExactly(committed);
                }
                assertThat(db.isConnected()).isFalse();
                assertThat(readFailure).isInstanceOf(IllegalStateException.class).hasMessage("connection is invalid");
            } finally {
                doCallRealMethod().when(db).close();
            }
        }
    }

    @Test
    void aFailedRollbackReleasesTheConnectionSoALaterReadCannotCommitItsUnfinishedWrites() {
        var path = directory.resolve("rollback.db");
        var committed = new ChatUserFilterEntity("123", "original", "committed");
        var failure = new IllegalStateException("update failed");
        var rollback = new IllegalStateException("rollback failed");
        try (var db = spy(new SQLiteDBConnector(path))) {
            db.connect();
            new ChatUserFilterEntityService(db).save(committed);
            doThrow(rollback).when(db).rollback();

            assertThatThrownBy(() -> db.transaction(() -> {
                db.execute("insert into chat_user_filter values('456', 'unfinished', 'must not be committed');");
                throw failure;
            })).isSameAs(failure).satisfies(error -> assertThat(error.getSuppressed()).containsExactly(rollback));

            var readFailure = catchThrowable(() -> new ChatUserFilterEntityService(db).retrieveAll());
            try (var observer = new SQLiteDBConnector(path)) {
                observer.connect();
                assertThat(new ChatUserFilterEntityService(observer).retrieveAll()).containsExactly(committed);
            }
            assertThat(db.isConnected()).isFalse();
            assertThat(readFailure)
                    .isInstanceOf(IllegalStateException.class).hasMessage("connection is null");
        }
        try (var fresh = new SQLiteDBConnector(path)) {
            fresh.connect();
            assertThat(new ChatUserFilterEntityService(fresh).retrieveAll()).containsExactly(committed);
        }
    }
}
