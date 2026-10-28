package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.SQLFunction;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFilterSnapshotTest {
    @TempDir Path directory;

    @Test
    void bothFilterTablesBelongToTheSameSnapshotDuringAConcurrentCommit() throws Exception {
        var path = directory.resolve("filters.db");
        var user = new ChatUserFilterEntity("123", "user", "comment");
        var changes = new ChatFilterEntityStore.Changes(
                List.of(new ChatKeywordFilterEntity(0, 0, "keyword")), Set.of(), List.of(user), Set.of());
        try (var writer = new SQLiteDBConnector(path);
             var reader = new PausedReader(path);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            writer.connect();
            reader.connect();
            writer.execute("pragma busy_timeout = 0;");
            writer.commit();
            var writes = new ChatFilterEntityStore(writer);
            var read = executor.submit(new ChatFilterEntityStore(reader)::read);
            try {
                await(reader.keywordRead);
                try {
                    writes.save(changes, () -> {});
                } catch (RuntimeException failure) {
                    // DELETE journalでは読取中のcommitは待機せずbusyになる。読取終了後に再試行する。
                    assertThat(failure).hasCauseInstanceOf(SQLiteException.class);
                    assertThat(((SQLiteException) failure.getCause()).getResultCode()).isEqualTo(SQLiteErrorCode.SQLITE_BUSY);
                }
            } finally {
                reader.release.countDown();
            }

            var snapshot = read.get(5, TimeUnit.SECONDS);
            assertThat(snapshot.keywords()).isEmpty();
            assertThat(snapshot.users()).isEmpty();
            var saved = writes.save(changes, () -> {});
            assertThat(saved.keywords()).extracting(ChatKeywordFilterEntity::keyword).containsExactly("keyword");
            assertThat(saved.users()).containsExactly(user);
            var refreshed = new ChatFilterEntityStore(reader).read();
            assertThat(refreshed.keywords()).isEqualTo(saved.keywords());
            assertThat(refreshed.users()).isEqualTo(saved.users());
        }
    }

    private static final class PausedReader extends SQLiteDBConnector {
        private final CountDownLatch keywordRead = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean keywordCompleted = new AtomicBoolean();
        private final AtomicBoolean pauseOnce = new AtomicBoolean(true);

        private PausedReader(Path path) { super(path); }

        @Override public synchronized void query(String sql, SQLFunction<ResultSet> mapping) {
            super.query(sql, mapping);
            if (sql.contains("from chat_keyword_filter")) keywordCompleted.set(true);
        }

        @Override public synchronized void readTransaction(Runnable action) {
            super.readTransaction(action);
            if (keywordCompleted.getAndSet(false) && pauseOnce.getAndSet(false)) {
                keywordRead.countDown();
                await(release);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
