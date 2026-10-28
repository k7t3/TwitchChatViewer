package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.database.SQLiteDBConnector;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.entity.service.ChatUserFilterEntityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class AppHelperShutdownTest {

    @TempDir
    Path directory;

    @Test
    void anOwnedDatabaseCannotBeReplacedOrAttachedAfterShutdown() {
        var helper = new AppHelper(mock(TaskWorker.class), mock(EventPublishers.class), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        var original = new UserDataFile(directory.resolve("original.db"));
        var replacement = new UserDataFile(directory.resolve("replacement.db"));
        helper.setUserDataFile(original);
        assertThatThrownBy(() -> helper.setUserDataFile(replacement)).isInstanceOf(IllegalStateException.class);
        assertThat(helper.getUserDataFile()).isSameAs(original);
        helper.close();
        assertThatThrownBy(() -> helper.setUserDataFile(replacement)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closedApplicationCannotCreateAnImageService() {
        var worker = mock(TaskWorker.class);
        when(worker.getExecutor()).thenReturn(Runnable::run);
        var helper = new AppHelper(worker, mock(EventPublishers.class), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        try {
            helper.close();

            assertThatThrownBy(helper::getImageLoader)
                    .isInstanceOf(IllegalStateException.class).hasMessage("application is closed");
            verify(worker, never()).getExecutor();
        } finally {
            helper.close();
        }
    }

    @Test
    void acceptedWriteIsPersistedBeforeDatabaseCloses() throws Exception {
        var file = new UserDataFile(directory.resolve("userdata.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        var worker = spy(new TaskWorker());
        var helper = new AppHelper(worker, mock(EventPublishers.class), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        helper.setUserDataFile(file);
        var service = new ChatUserFilterEntityService(file.getConnector());
        var filter = new ChatUserFilterEntity("123", "user", "saved during shutdown");
        var release = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        var saved = new CompletableFuture<Void>();
        doAnswer(invocation -> {
            draining.countDown();
            return invocation.callRealMethod();
        }).when(worker).close();
        worker.getExecutor().execute(() -> {
            try {
                release.await();
                service.save(filter);
                saved.complete(null);
            } catch (Throwable failure) {
                saved.completeExceptionally(failure);
            }
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var closing = executor.submit(helper::close);
            try {
                assertThat(draining.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(closing.isDone()).isFalse();
                release.countDown();
                saved.get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);

                try (var reopened = new SQLiteDBConnector(file.getFilePath())) {
                    reopened.connect();
                    assertThat(new ChatUserFilterEntityService(reopened).retrieveAll()).containsExactly(filter);
                }
            } finally {
                release.countDown();
                closing.get(5, TimeUnit.SECONDS);
            }
        } finally {
            worker.close();
            file.closeDatabase();
        }
    }

    @Test
    void closeAttemptsAllResourcesAndPreservesFailures() throws Exception {
        var file = new UserDataFile(directory.resolve("userdata.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        var worker = mock(TaskWorker.class);
        var publishers = mock(EventPublishers.class);
        var twitch = mock(Twitch.class);
        var helper = new AppHelper(worker, publishers, unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        helper.setUserDataFile(file);
        helper.setTwitch(twitch);
        var first = new IllegalStateException("worker shutdown failed");
        var second = new IllegalArgumentException("client shutdown failed");
        doThrow(first).when(worker).close();
        doThrow(second).when(twitch).close();
        try {
            assertThatThrownBy(helper::close).isSameAs(first);

            verify(twitch).close();
            verify(publishers).close();
            assertThat(file.getConnector().isConnected()).isFalse();
            assertThat(first.getSuppressed()).containsExactly(second);
        } finally {
            file.closeDatabase();
        }
    }

    @Test
    void credentialFreezeFailureStillCompletesShutdownAndIsSharedByLaterClosers() throws Exception {
        var file = new UserDataFile(directory.resolve("failed-freeze.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        var worker = mock(TaskWorker.class);
        var publishers = mock(EventPublishers.class);
        var channels = mock(ChannelRepository.class);
        var twitch = mock(Twitch.class);
        var helper = new AppHelper(worker, publishers, client -> channels,
                unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        helper.setUserDataFile(file);
        helper.setTwitch(twitch);
        var failure = new IllegalStateException("cannot stop credential updates");
        doThrow(failure).when(twitch).freezeCredentials();
        try {
            assertThatThrownBy(helper::close).isSameAs(failure);

            verify(worker).close();
            verify(channels).clear();
            verify(twitch).close();
            verify(publishers).close();
            assertThat(file.getConnector().isConnected()).isFalse();

            assertThatThrownBy(helper::close).isSameAs(failure);
            verify(worker).close();
            verify(channels).clear();
            verify(twitch).close();
            verify(publishers).close();
        } finally {
            file.closeDatabase();
        }
    }

    @Test
    void databaseRemainsOpenUntilWorkerHasDrained() throws Exception {
        var file = new UserDataFile(directory.resolve("userdata.db"));
        file.connectDatabase(DatabaseVersion.EMPTY);
        var worker = mock(TaskWorker.class);
        var helper = new AppHelper(worker, mock(EventPublishers.class), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); });
        helper.setUserDataFile(file);
        doAnswer(invocation -> {
            assertThat(file.getConnector().isConnected()).as("DB while draining accepted work").isTrue();
            return null;
        }).when(worker).close();
        try {
            helper.close();

            assertThat(file.getConnector().isConnected()).isFalse();
        } finally {
            file.closeDatabase();
        }
    }
}
