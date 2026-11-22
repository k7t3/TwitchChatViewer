/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.service;

import javafx.concurrent.Worker;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class FXTaskTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aSessionCancellationIsACancelledTaskAndDoesNotReportAFailure(boolean runnable, FxRobot robot) {
        var failures = new ArrayList<Throwable>();
        var finalizations = new AtomicInteger();
        Runnable cancelled = () -> { throw new java.util.concurrent.CancellationException("session ended"); };
        FXTask<?> task = runnable ? FXTask.task(cancelled) : FXTask.task(() -> { cancelled.run(); return "unused"; });
        robot.interact(() -> {
            task.onFailure(failures::add);
            task.setFinally(finalizations::incrementAndGet);
        });
        task.run();
        robot.interact(() -> {
            assertThat(task.getState()).isEqualTo(Worker.State.CANCELLED);
            assertThat(failures).isEmpty();
            assertThat(finalizations).hasValue(1);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failureHandlerReceivesTheOriginalFailureOnFxEvenWhenRegisteredLate(boolean late, FxRobot robot) {
        var failure = new IllegalStateException("fetch failed");
        var task = FXTask.<String>task(() -> { throw failure; });
        var failures = new ArrayList<Throwable>();
        java.util.function.Consumer<Throwable> handler = error -> {
            assertThat(javafx.application.Platform.isFxApplicationThread()).isTrue();
            failures.add(error);
        };
        if (!late) robot.interact(() -> task.onFailure(handler));
        task.run();
        robot.interact(() -> assertThat(task.getState()).isEqualTo(Worker.State.FAILED));
        if (late) robot.interact(() -> task.onFailure(handler));
        robot.interact(() -> assertThat(failures).containsExactly(failure));
    }

    @Test
    void cancellationDoesNotCallTheFailureHandler(FxRobot robot) {
        var failures = new ArrayList<Throwable>();
        var task = FXTask.task(() -> "never started");
        robot.interact(() -> {
            task.onFailure(failures::add);
            task.cancel();
        });
        robot.interact(() -> {
            task.onFailure(failures::add);
            assertThat(failures).isEmpty();
        });
    }

    @Test
    void lateFinallyHandlerRunsAfterCancellation(FxRobot robot) {
        var task = FXTask.task(() -> "never started");
        var count = new AtomicInteger();
        robot.interact(() -> { task.cancel(); });

        robot.interact(() -> task.setFinally(count::incrementAndGet));

        robot.interact(() -> assertThat(count.get()).isEqualTo(1));
    }

    @Test
    void lateSuccessHandlerReceivesAlreadyCompletedValue(FxRobot robot) {
        var task = new AtomicReference<FXTask<String>>();
        var values = new ArrayList<String>();
        robot.interact(() -> task.set(FXTask.of("completed")));
        robot.interact(() -> assertThat(task.get().getState()).isEqualTo(Worker.State.SUCCEEDED));

        robot.interact(() -> task.get().onDone(values::add));

        robot.interact(() -> assertThat(values).containsExactly("completed"));
    }

    @Test
    void empty(FxRobot robot) throws Exception {
        robot.interact(() -> {
            var task = FXTask.empty();
            assertNull(task.getValue());
        });
    }

    @Test
    void of(FxRobot robot) {
        robot.interact(() -> {
            var task = FXTask.of(100);
            Integer result = null;
            try {
                result = task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            assertEquals(100, result);
        });
    }
}
