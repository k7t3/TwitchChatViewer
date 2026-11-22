package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import com.github.k7t3.tcv.view.core.WindowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class WindowBoundsServiceOwnershipTest {
    @Test
    void boundsWritesRemainInAcceptanceOrderWhenTheExecutorRunsJobsBackwards(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.service.saveBoundsAsync("room", bounds(1));
                f.result = f.service.saveBoundsAsync("room", bounds(2));
            });
            f.runBackwards(); f.result.get(5, TimeUnit.SECONDS);
            assertThat(f.saved).containsEntry("room", entity(2));
        }
    }

    @Test
    void cancellingTheObserverDoesNotCancelAnAcceptedBoundsWrite(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> { f.result = f.service.saveBoundsAsync("room", bounds(1)); f.result.cancel(true); });
            f.runBackwards(); assertThat(f.saved).containsEntry("room", entity(1));
        }
    }

    @Test
    void aReadFollowsAcceptedWritesEvenIfItsWorkerRunsBeforeTheirPump(FxRobot robot) throws Exception {
        try (var f = new Fixture(robot)) {
            var read = new FXTask<?>[1];
            robot.interact(() -> {
                f.service.saveBoundsAsync("room", bounds(2)); read[0] = f.service.getBoundsAsync("room");
            });
            f.runBackwards(); assertThat(read[0].get(5, TimeUnit.SECONDS)).isEqualTo(bounds(2));
        }
    }

    @Test
    void closingTheServiceRejectsNewWorkButCompletesAcceptedWrites(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            robot.interact(() -> {
                f.service.saveBoundsAsync("room", bounds(1)); f.service.close();
                assertThatThrownBy(() -> f.service.saveBoundsAsync("room", bounds(2))).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> f.service.getBoundsAsync("room")).isInstanceOf(IllegalStateException.class);
            });
            f.runBackwards(); assertThat(f.saved).containsEntry("room", entity(1));
        }
    }

    @Test
    void aCancelledObserverStillRegistersTheOriginalWriteFailureForShutdown(FxRobot robot) {
        try (var f = new Fixture(robot)) {
            var failure = new IllegalStateException("bounds write failed");
            doThrow(failure).when(f.entities).save(any());
            robot.interact(() -> { f.result = f.service.saveBoundsAsync("room", bounds(1)); f.result.cancel(true); });
            f.runBackwards();
            assertThat(f.drains).hasSize(1);
            assertThatThrownBy(() -> f.drains.getFirst().toCompletableFuture().get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(f.errors).containsExactly(failure);
        }
    }

    private static WindowBounds bounds(double x) { return new WindowBounds(x, 2, 360, 640, false); }
    private static WindowBoundsEntity entity(double x) { return new WindowBoundsEntity("room", x, 2, 360, 640, false); }

    private static final class Fixture implements AutoCloseable {
        final FxRobot robot;
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final WindowBoundsEntityService entities = mock(WindowBoundsEntityService.class);
        final Map<String,WindowBoundsEntity> saved = new ConcurrentHashMap<>();
        final List<CompletionStage<Void>> drains = new ArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final WindowBoundsService service;
        FXTask<?> result;
        Fixture(FxRobot robot) {
            this.robot = robot;
            doAnswer(invocation -> { WindowBoundsEntity entity = invocation.getArgument(0); saved.put(entity.identity(),entity); return null; }).when(entities).save(any());
            when(entities.get("room")).thenAnswer(ignored -> saved.getOrDefault("room", entity(0)));
            service = new WindowBoundsService(entities, queue::add, drains::add, errors::add);
        }
        void runBackwards() { Runnable task; while ((task = queue.pollLast()) != null) task.run(); }
        @Override public void close() { robot.interact(service::close); runBackwards(); }
    }
}
