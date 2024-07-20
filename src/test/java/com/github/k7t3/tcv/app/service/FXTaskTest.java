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
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class FXTaskTest {

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