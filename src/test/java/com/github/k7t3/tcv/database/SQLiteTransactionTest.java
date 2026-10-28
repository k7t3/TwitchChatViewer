package com.github.k7t3.tcv.database;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteTransactionTest {

    @Test
    void rejectsAutoCommitBeforeRunningUpdates() {
        try (var connector = new TestDBConnector()) {
            connector.connect();
            connector.execute("create table transaction_values (value integer);");
            connector.commit();
            connector.setAutoCommit(true);

            assertThatThrownBy(() -> connector.transaction(() ->
                    connector.execute("insert into transaction_values values (1);")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("auto-commit");

            var values = new ArrayList<Integer>();
            connector.query("select value from transaction_values;", rs -> {
                while (rs.next()) values.add(rs.getInt(1));
            });
            assertThat(values).isEmpty();
        }
    }

    @Test
    void rejectsNestedTransactionBeforeItCanCommitOuterChanges() {
        try (var connector = new TestDBConnector()) {
            connector.connect();
            connector.execute("create table transaction_values (value integer);");
            connector.commit();

            assertThatThrownBy(() -> connector.transaction(() -> {
                connector.execute("insert into transaction_values values (1);");
                connector.transaction(() -> connector.execute("insert into transaction_values values (2);"));
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("nested");

            var values = new ArrayList<Integer>();
            connector.query("select value from transaction_values;", rs -> {
                while (rs.next()) values.add(rs.getInt(1));
            });
            assertThat(values).isEmpty();
        }
    }

    @Test
    void serializesTransactionsSharingOneConnection() throws Exception {
        try (var connector = new TestDBConnector();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            connector.connect();
            connector.execute("create table transaction_values (value integer);");
            connector.commit();
            var firstWrite = new CountDownLatch(1);
            var releaseFirst = new CountDownLatch(1);
            var secondStarted = new CountDownLatch(1);
            var first = executor.submit(() -> connector.transaction(() -> {
                connector.execute("insert into transaction_values values (1);");
                firstWrite.countDown();
                await(releaseFirst);
            }));
            try {
                await(firstWrite);
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    connector.transaction(() -> connector.execute("insert into transaction_values values (2);"));
                });
                await(secondStarted);

                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);

                releaseFirst.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                var values = new ArrayList<Integer>();
                connector.query("select value from transaction_values order by value;", rs -> {
                    while (rs.next()) values.add(rs.getInt(1));
                });
                assertThat(values).containsExactly(1, 2);
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
