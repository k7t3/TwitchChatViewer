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

package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.prefs.AppPreferences;
import de.saxsys.mvvmfx.ViewModel;

import java.nio.file.Path;

public class PreferencesViewModel implements ViewModel {

    private final AppPreferences prefs = AppPreferences.getInstance();

    public void saveAsync() {
        var task = FXTask.task(prefs::save);
        task.runAsync();
    }

    public FXTask<Void> importAsync(Path filePath) {
        var task = FXTask.task(() -> prefs.importPreferences(filePath));
        task.runAsync();
        return task;
    }

    public FXTask<Void> exportAsync(Path filePath) {
        var task = FXTask.task(() -> prefs.exportPreferences(filePath));
        task.runAsync();
        return task;
    }

    public FXTask<Void> clearAsync() {
        var task = FXTask.task(prefs::clear);
        task.runAsync();
        return task;
    }

}
