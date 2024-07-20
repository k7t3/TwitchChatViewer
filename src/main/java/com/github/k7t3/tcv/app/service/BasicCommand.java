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

import de.saxsys.mvvmfx.utils.commands.CommandBase;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

public abstract class BasicCommand extends CommandBase implements EventHandler<ActionEvent> {

    public static BasicCommand of(Runnable runnable) {
        return new BasicCommand() {
            @Override
            public void execute() {
                runnable.run();
            }
        };
    }

    public static BasicCommand of(Runnable runnable, ObservableValue<Boolean> executableCondition) {
        var command = new BasicCommand() {
            @Override
            public void execute() {
                runnable.run();
            }
        };
        command.executable.bind(executableCondition);
        return command;
    }

    @Override
    public void handle(ActionEvent actionEvent) {
        actionEvent.consume();
        execute();
    }

    @Override
    public double getProgress() {
        return -1.0;
    }

    @Override
    public ReadOnlyDoubleProperty progressProperty() {
        throw new UnsupportedOperationException();
    }
}
