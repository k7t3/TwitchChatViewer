package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.service.TaskWorker;
import org.junit.jupiter.api.AutoClose;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import javafx.application.Platform;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import java.util.*;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupWriteOwnershipTest {
    @AutoClose
    private final TaskWorker worker = new TaskWorker();

    @Test
    void anEarlierFailedUpdateCannotUndoALaterAcceptedWrite(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = fixture.seed();
            doThrow(new IllegalStateException("first failed")).doAnswer(fixture::save)
                    .when(fixture.service).save(any(), any());
            robot.interact(() -> {
                group.setName("failed"); fixture.repository.saveAsync(group);
                group.setName("accepted"); fixture.repository.saveAsync(group);
            });
            fixture.runIo();
            robot.interact(() -> assertThat(group.getName()).isEqualTo("accepted"));
            assertThat(fixture.saved.getFirst().name()).isEqualTo("accepted");
        }
    }

    @Test
    void aCreateFollowedByDeleteBeforeCommitCannotLeaveAPhantomGroup(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = new ChannelGroup(); group.setName("created and removed");
            robot.interact(() -> {
                fixture.repository.saveAsync(group);
                fixture.repository.removeAsync(group);
                assertThat(fixture.repository.getAll()).isEmpty();
            });
            fixture.runIo();
            verify(fixture.service).remove(argThat(entity -> entity.id().equals(fixture.saved.getFirst().id())));
            robot.interact(() -> {
                assertThat(fixture.repository.getAll()).isEmpty();
                fixture.repository.applySnapshot(List.copyOf(fixture.saved), Map.of());
                assertThat(fixture.repository.getAll()).isEmpty();
            });
        }
    }

    @Test
    void loadSnapshotDoesNotShareTheServicesMutableChannelIdSets(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var ids = new HashSet<>(Set.of("123"));
            var base = entity();
            when(fixture.service.retrieveAll()).thenReturn(List.of(new ChannelGroupEntity(base.id(), base.name(),
                    base.comment(), base.pinned(), base.createdAt(), base.updatedAt(), ids)));
            var snapshot = fixture.pool.submit(fixture.repository::loadSnapshot).get(5, TimeUnit.SECONDS);
            ids.clear();
            assertThat(snapshot.getFirst().channelIds()).containsExactly("123");
            assertThatThrownBy(() -> snapshot.getFirst().channelIds().add("late")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void inputIsCapturedOnFxAndLaterEditsCannotChangeTheAcceptedWrite(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = fixture.seed();
            var channel = mock(TwitchChannelViewModel.class);
            when(channel.getBroadcaster()).thenAnswer(invocation -> {
                assertThat(Platform.isFxApplicationThread()).isTrue();
                return new Broadcaster("123", "user", "User", "", "");
            });
            var task = new AtomicReference<FXTask<?>>();
            robot.interact(() -> {
                group.setName("accepted"); group.setComment("accepted comment"); group.setPinned(true);
                group.getChannels().add(channel);
                task.set(fixture.repository.saveAsync(group));
                group.setName("next draft"); group.getChannels().clear();
            });
            fixture.runIo(); task.get().get(5, TimeUnit.SECONDS);
            var saved = fixture.saved.getFirst();
            assertThat(saved.name()).isEqualTo("accepted");
            assertThat(saved.comment()).isEqualTo("accepted comment");
            assertThat(saved.pinned()).isTrue();
            assertThat(saved.channelIds()).containsExactly("123");
            robot.interact(() -> assertThat(group.getName()).isEqualTo("next draft"));
        }
    }

    @Test
    void repeatedSavesBeforeFirstCommitUseOneInsertAndThenAnUpdate(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = new ChannelGroup();
            robot.interact(() -> {
                group.setName("first"); fixture.repository.saveAsync(group);
                group.setName("second"); fixture.repository.saveAsync(group);
                assertThat(group.getId()).isNull();
            });
            fixture.runIo();
            assertThat(fixture.types).containsExactly(SaveType.INSERT, SaveType.UPDATE);
            assertThat(fixture.saved.getFirst().id()).isEqualTo(fixture.saved.getLast().id());
            assertThat(fixture.saved.getFirst().name()).isEqualTo("first");
            assertThat(fixture.saved.getLast().name()).isEqualTo("second");
            robot.interact(() -> {
                assertThat(fixture.repository.getAll()).containsExactly(group);
                assertThat(group.getId()).isEqualTo(fixture.saved.getFirst().id());
                assertThat(group.getUpdatedAt()).isEqualTo(fixture.saved.getLast().updatedAt());
            });
        }
    }

    @Test
    void failedInsertLeavesNoPhantomGroupAndCanRetryTheInsert(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var failure = new IllegalStateException("insert failed");
            doThrow(failure).doAnswer(fixture::save).when(fixture.service).save(any(), any());
            var group = new ChannelGroup(); group.setName("draft");
            var request = new AtomicReference<FXTask<?>>();
            robot.interact(() -> request.set(fixture.repository.saveAsync(group)));
            fixture.runIo();
            assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> {
                assertThat(group.getId()).isNull(); assertThat(group.getCreatedAt()).isNull();
                assertThat(fixture.repository.getAll()).isEmpty();
                assertThat(fixture.errors).containsExactly(failure);
                request.set(fixture.repository.saveAsync(group));
            });
            fixture.runIo(); request.get().get(5, TimeUnit.SECONDS);
            assertThat(fixture.types).containsExactly(SaveType.INSERT);
            robot.interact(() -> assertThat(fixture.repository.getAll()).containsExactly(group));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedUpdateRestoresCommittedFieldsButKeepsANewerUnsavedEdit(boolean newer, FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = fixture.seed();
            var originalTime = group.getUpdatedAt();
            var failure = new IllegalStateException("update failed");
            doThrow(failure).when(fixture.service).save(any(), any());
            var request = new AtomicReference<FXTask<?>>();
            robot.interact(() -> {
                group.setName("failed draft"); group.setComment("failed comment"); group.setPinned(true);
                request.set(fixture.repository.saveAsync(group));
                if (newer) group.setName("newer unsaved edit");
            });
            fixture.runIo();
            assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> {
                assertThat(group.getName()).isEqualTo(newer ? "newer unsaved edit" : "original");
                assertThat(group.getComment()).isEqualTo("original comment");
                assertThat(group.isPinned()).isFalse();
                assertThat(group.getUpdatedAt()).isEqualTo(originalTime);
                assertThat(fixture.repository.getAll()).containsExactly(group);
            });
        }
    }

    @Test
    void laterFailureRollsBackToTheEarlierSuccessfulWriteInsteadOfTheInitialLoad(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = fixture.seed();
            var failure = new IllegalStateException("second update failed");
            doAnswer(fixture::save).doThrow(failure).when(fixture.service).save(any(), any());
            robot.interact(() -> {
                group.setName("first accepted"); fixture.repository.saveAsync(group);
                group.setName("second rejected"); fixture.repository.saveAsync(group);
            });
            fixture.runIo();
            robot.interact(() -> {
                assertThat(group.getName()).isEqualTo("first accepted");
                assertThat(group.getUpdatedAt()).isEqualTo(fixture.saved.getFirst().updatedAt());
            });
        }
    }

    @Test
    void deletePublishesOnlyAfterCommitAndAFailedDeleteKeepsTheGroup(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = fixture.seed();
            var failure = new IllegalStateException("delete failed");
            doThrow(failure).doNothing().when(fixture.service).remove(any());
            var request = new AtomicReference<FXTask<?>>();
            robot.interact(() -> {
                request.set(fixture.repository.removeAsync(group));
                assertThat(fixture.repository.getAll()).containsExactly(group);
            });
            fixture.runIo();
            assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
            robot.interact(() -> {
                assertThat(fixture.repository.getAll()).containsExactly(group);
                request.set(fixture.repository.removeAsync(group));
            });
            fixture.runIo(); request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> assertThat(fixture.repository.getAll()).isEmpty());
        }
    }

    @Test
    void cancellingTheObserverDoesNotCancelTheWriteOrItsPublication(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = new ChannelGroup(); group.setName("accepted");
            robot.interact(() -> fixture.repository.saveAsync(group).cancel(true));
            fixture.runIo();
            assertThat(fixture.saved).hasSize(1);
            robot.interact(() -> assertThat(fixture.repository.getAll()).containsExactly(group));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void acceptedWritesFinishAfterScopeEndsWithoutPublishingOldModels(boolean close, boolean fail, FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = new ChannelGroup(); group.setName("accepted");
            var failure = new IllegalStateException("late save failure");
            if (fail) doThrow(failure).when(fixture.service).save(any(), any());
            var request = new AtomicReference<FXTask<?>>();
            robot.interact(() -> {
                request.set(fixture.repository.saveAsync(group));
                if (close) fixture.repository.close(); else fixture.repository.onLogout();
            });
            fixture.runIo();
            if (fail) {
                assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
                assertThatThrownBy(() -> fixture.actual.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
            } else request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(fixture.repository.getAll()).isEmpty(); assertThat(group.getId()).isNull();
                assertThat(fixture.errors).isEmpty();
                if (close) assertThatThrownBy(() -> fixture.repository.saveAsync(group)).isInstanceOf(IllegalStateException.class);
            });
            verify(fixture.service).save(any(), any());
        }
    }

    @Test
    void aBackgroundSnapshotDrainsEarlierQueuedWritesAndCannotOverwriteALocalEdit(FxRobot robot) throws Exception {
        try (var fixture = new Fixture(robot)) {
            var group = fixture.seed();
            when(fixture.service.retrieveAll()).thenAnswer(invocation -> List.copyOf(fixture.saved));
            robot.interact(() -> { group.setName("saved"); fixture.repository.saveAsync(group); });
            var snapshot = fixture.pool.submit(fixture.repository::loadSnapshot).get(5, TimeUnit.SECONDS);
            assertThat(snapshot).hasSize(1); assertThat(snapshot.getFirst().name()).isEqualTo("saved");
            robot.interact(() -> {
                group.setName("next draft"); fixture.repository.saveAsync(group);
                fixture.repository.applySnapshot(snapshot, Map.of());
                assertThat(fixture.repository.getAll()).containsExactly(group);
                assertThat(group.getName()).isEqualTo("next draft");
            });
            fixture.runIo();
        }
    }

    @Test
    void rejectedWriteRestoresTheCommittedGroupAndRetainsTheCause(FxRobot robot) {
        var failure = new RejectedExecutionException("executor stopped");
        var errors = new ArrayList<Throwable>();
        var repository = new ChannelGroupRepository(mock(ChannelGroupEntityService.class), task -> { throw failure; }, ignored -> {}, errors::add);
        var group = new AtomicReference<ChannelGroup>();
        robot.interact(() -> {
            repository.applySnapshot(List.of(entity()), Map.of());
            group.set(repository.getAll().getFirst()); group.get().setName("rejected draft");
            var result = repository.saveAsync(group.get());
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasCause(failure);
        });
        try { robot.interact(() -> assertThat(group.get().getName()).isEqualTo("original")); }
        finally { robot.interact(repository::close); }
    }

    @Test
    void aNewGroupIsPublishedOnlyAfterTheInsertSucceeds(FxRobot robot) throws Exception {
        var service = mock(ChannelGroupEntityService.class);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release insert");
            return null;
        }).when(service).save(any(SaveType.class), any(ChannelGroupEntity.class));
        var repository = new ChannelGroupRepository(service, worker.getExecutor(), ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var group = new ChannelGroup(); group.setName("new group");
        var request = new AtomicReference<FXTask<?>>();
        try {
            robot.interact(() -> {
                request.set(repository.saveAsync(group));
                assertThat(repository.getAll()).isEmpty();
                assertThat(group.getId()).isNull();
                assertThat(group.getCreatedAt()).isNull();
                assertThat(group.getUpdatedAt()).isNull();
            });
            release.countDown();
            request.get().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {
                assertThat(repository.getAll()).containsExactly(group);
                assertThat(group.getId()).isNotNull();
                assertThat(group.getUpdatedAt()).isEqualTo(group.getCreatedAt());
            });
        } finally {
            release.countDown();
            if (request.get() != null) request.get().get(5, TimeUnit.SECONDS);
            robot.interact(repository::close);
        }
    }

    private static ChannelGroupEntity entity() {
        var time = LocalDateTime.of(2024, 1, 1, 0, 0);
        return new ChannelGroupEntity(UUID.randomUUID(), "original", "original comment", false, time, time, Set.of());
    }

    private static final class Fixture implements AutoCloseable {
        final FxRobot robot;
        final ChannelGroupEntityService service = mock(ChannelGroupEntityService.class);
        final LinkedBlockingQueue<Runnable> scheduled = new LinkedBlockingQueue<>();
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        final List<ChannelGroupEntity> saved = new CopyOnWriteArrayList<>();
        final List<SaveType> types = new CopyOnWriteArrayList<>();
        final List<CompletionStage<Void>> actual = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        final ChannelGroupRepository repository;
        Fixture(FxRobot robot) {
            this.robot = robot;
            repository = new ChannelGroupRepository(service, scheduled::add, actual::add, errors::add);
            doAnswer(this::save).when(service).save(any(), any());
        }
        Object save(org.mockito.invocation.InvocationOnMock invocation) {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            types.add(invocation.getArgument(0)); saved.add(invocation.getArgument(1)); return null;
        }
        ChannelGroup seed() {
            var result = new AtomicReference<ChannelGroup>();
            robot.interact(() -> { repository.applySnapshot(List.of(entity()), Map.of()); result.set(repository.getAll().getFirst()); });
            return result.get();
        }
        void runIo() throws Exception {
            var pump = scheduled.poll(5, TimeUnit.SECONDS);
            assertThat(pump).isNotNull(); pool.submit(pump).get(5, TimeUnit.SECONDS);
            for (var stage : actual) stage.handle((ignored, failure) -> null).toCompletableFuture().get(5, TimeUnit.SECONDS);
            robot.interact(() -> {});
        }
        @Override public void close() {
            robot.interact(repository::close);
            Runnable next;
            while ((next = scheduled.poll()) != null) {
                try { pool.submit(next).get(5, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(failure); }
            }
            pool.close();
        }
    }
}
