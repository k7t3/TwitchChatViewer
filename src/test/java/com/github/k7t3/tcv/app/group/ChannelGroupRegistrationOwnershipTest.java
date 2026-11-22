package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.database.TestDBConnector;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupRegistrationOwnershipTest {
    @ParameterizedTest(name = "new group registration Error={0}")
    @ValueSource(booleans = {false, true})
    void anUnacceptedNewGroupLeavesNoRowAndItsFirstAcceptedRetryKeepsCreationTime(boolean fatal, FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var service = new ChannelGroupEntityService(db);
            var queue = new LinkedBlockingQueue<Runnable>();
            var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
            var errors = new CopyOnWriteArrayList<Throwable>();
            var reject = new AtomicBoolean(true);
            Throwable failure = fatal ? new LinkageError("registration unavailable") : new IllegalStateException("registration unavailable");
            var repository = new ChannelGroupRepository(service, queue::add, stage -> {
                stages.add(stage);
                if (reject.getAndSet(false)) {
                    if (failure instanceof RuntimeException exception) throw exception;
                    throw (Error) failure;
                }
            }, errors::add);
            var group = new ChannelGroup();
            var request = new AtomicReference<FXTask<?>>();
            try {
                robot.interact(() -> {
                    group.setName("refused");
                    request.set(repository.saveAsync(group));
                });
                if (fatal) assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasRootCause(failure);
                else assertThatThrownBy(() -> request.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
                assertThat(stages.getFirst().toCompletableFuture().isCompletedExceptionally()).isTrue();
                assertThat(service.retrieveAll()).isEmpty();
                assertThat(queue).isEmpty();
                robot.interact(() -> {
                    assertThat(request.get().getException()).isSameAs(failure);
                    assertThat(group.getId()).isNull();
                    assertThat(group.getCreatedAt()).isNull();
                    assertThat(group.getUpdatedAt()).isNull();
                    assertThat(repository.getAll()).isEmpty();
                    assertThat(errors).containsExactly(failure);
                    group.setName("accepted");
                    request.set(repository.saveAsync(group));
                });
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                request.get().get(5, TimeUnit.SECONDS);
                var stored = service.retrieveAll();
                assertThat(stored).singleElement().satisfies(row -> {
                    assertThat(row.name()).isEqualTo("accepted");
                    assertThat(row.comment()).isEmpty();
                    assertThat(row.pinned()).isFalse();
                    assertThat(row.channelIds()).isEmpty();
                    assertThat(row.updatedAt()).isEqualTo(row.createdAt());
                });
                robot.interact(() -> {
                    assertThat(repository.getAll()).containsExactly(group);
                    assertThat(group.getId()).isEqualTo(stored.getFirst().id());
                    assertThat(group.getUpdatedAt()).isEqualTo(group.getCreatedAt());
                    assertThat(errors).containsExactly(failure);
                });
                stages.getLast().toCompletableFuture().get(5, TimeUnit.SECONDS);
            } finally {
                robot.interact(repository::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void rejectedLaterRegistrationCannotSupersedeAnEarlierAcceptedWritesRollback(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var service = new ChannelGroupEntityService(db);
            var created = LocalDateTime.of(2026, 10, 24, 7, 0);
            var original = new ChannelGroupEntity(UUID.fromString("28666bfc-c932-4e1b-8723-119f4e1d9875"),
                    "original", "original comment", false, created, created, Set.of());
            service.save(SaveType.INSERT, original);
            db.execute("""
                    CREATE TRIGGER fail_group_update BEFORE UPDATE ON groups WHEN NEW.name = 'failed accepted'
                    BEGIN SELECT RAISE(ABORT, 'injected group update failure'); END;
                    """);
            db.commit();
            var initial = service.retrieveAll();
            var queue = new LinkedBlockingQueue<Runnable>();
            var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
            var errors = new CopyOnWriteArrayList<Throwable>();
            var registrations = new AtomicInteger();
            var registrationFailure = new IllegalStateException("second registration rejected");
            var repository = new ChannelGroupRepository(service, queue::add, stage -> {
                stages.add(stage);
                if (registrations.incrementAndGet() == 2) throw registrationFailure;
            }, errors::add);
            var accepted = new AtomicReference<FXTask<?>>();
            var rejected = new AtomicReference<FXTask<?>>();
            var group = new AtomicReference<ChannelGroup>();
            try {
                robot.interact(() -> {
                    repository.applySnapshot(initial, Map.of());
                    group.set(repository.getAll().getFirst());
                    group.get().setName("failed accepted");
                    accepted.set(repository.saveAsync(group.get()));
                    group.get().setComment("unaccepted comment");
                    group.get().setPinned(true);
                    rejected.set(repository.saveAsync(group.get()));
                });
                assertThatThrownBy(() -> rejected.get().get(5, TimeUnit.SECONDS)).hasCause(registrationFailure);
                assertThat(stages.getFirst().toCompletableFuture().isDone()).isFalse();
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                var failedSave = catchThrowable(() -> accepted.get().get(5, TimeUnit.SECONDS));
                assertThat(failedSave).hasRootCauseMessage("[SQLITE_CONSTRAINT_TRIGGER] A RAISE function within a trigger fired, causing the SQL statement to abort (injected group update failure)");

                assertThat(service.retrieveAll()).containsExactly(original);
                robot.interact(() -> {
                    assertThat(group.get().getName()).isEqualTo("original");
                    assertThat(group.get().getComment()).isEqualTo("unaccepted comment");
                    assertThat(group.get().isPinned()).isTrue();
                    assertThat(group.get().getUpdatedAt()).isEqualTo(created);
                    assertThat(repository.getAll()).containsExactly(group.get());
                    assertThat(errors).containsExactly(registrationFailure, failedSave.getCause());
                });
            } finally {
                robot.interact(repository::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void rejectedDeletionRegistrationSettlesItsTaskAndLeavesTheStoredGroupEditable(FxRobot robot) throws Exception {
        try (var db = new TestDBConnector(); var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            db.connect();
            var service = new ChannelGroupEntityService(db);
            var id = UUID.fromString("203e29b0-1498-4470-a82b-cebe5c85df5e");
            var created = LocalDateTime.of(2026, 10, 24, 7, 0);
            var original = new ChannelGroupEntity(id, "before", "comment", false, created, created, Set.of());
            service.save(SaveType.INSERT, original);
            var initial = service.retrieveAll();
            var queue = new LinkedBlockingQueue<Runnable>();
            var stages = new CopyOnWriteArrayList<CompletionStage<Void>>();
            var errors = new CopyOnWriteArrayList<Throwable>();
            var reject = new AtomicBoolean(true);
            var failure = new IllegalStateException("shutdown registration rejected");
            var repository = new ChannelGroupRepository(service, queue::add, stage -> {
                stages.add(stage);
                if (reject.getAndSet(false)) throw failure;
            }, errors::add);
            var group = new AtomicReference<ChannelGroup>();
            var rejected = new AtomicReference<FXTask<?>>();
            var retry = new AtomicReference<FXTask<?>>();
            var disabled = new SimpleBooleanProperty();
            try {
                robot.interact(() -> {
                    repository.applySnapshot(initial, Map.of());
                    group.set(repository.getAll().getFirst());
                    disabled.set(true);

                    assertThatCode(() -> rejected.set(repository.removeAsync(group.get()))).doesNotThrowAnyException();
                    rejected.get().setFinally(() -> disabled.set(false));
                });
                assertThatThrownBy(() -> rejected.get().get(5, TimeUnit.SECONDS)).hasCause(failure);
                assertThat(stages.getFirst().toCompletableFuture().isDone()).isTrue();
                assertThatThrownBy(() -> stages.getFirst().toCompletableFuture().join()).hasCause(failure);
                assertThat(queue).isEmpty();
                assertThat(service.retrieveAll()).containsExactly(original);
                robot.interact(() -> {
                    assertThat(disabled.get()).isFalse();
                    assertThat(errors).containsExactly(failure);
                    assertThat(repository.getAll()).containsExactly(group.get());
                    group.get().setName("after");
                    retry.set(repository.saveAsync(group.get()));
                });
                worker.submit(queue.remove()).get(5, TimeUnit.SECONDS);
                retry.get().get(5, TimeUnit.SECONDS);

                var saved = service.retrieveAll().getFirst();
                assertThat(saved.id()).isEqualTo(id);
                assertThat(saved.name()).isEqualTo("after");
                assertThat(saved.comment()).isEqualTo("comment");
                assertThat(saved.pinned()).isFalse();
                assertThat(saved.createdAt()).isEqualTo(created);
                assertThat(saved.channelIds()).isEmpty();
                robot.interact(() -> {
                    assertThat(repository.getAll()).containsExactly(group.get());
                    assertThat(group.get().getName()).isEqualTo("after");
                    assertThat(errors).containsExactly(failure);
                });
            } finally {
                robot.interact(repository::close);
                Runnable task;
                while ((task = queue.poll()) != null) worker.submit(task).get(5, TimeUnit.SECONDS);
            }
        }
    }
}
