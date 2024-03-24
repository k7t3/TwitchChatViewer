package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
import de.saxsys.mvvmfx.ViewModel;

import java.nio.file.Path;

public class PreferencesViewModel implements ViewModel {

    private final AppPreferences prefs = AppPreferences.getInstance();

    public void saveAsync() {
        var task = FXTask.task(prefs::save);
        TaskWorker.getInstance().submit(task);
    }

    public FXTask<Void> importAsync(Path filePath) {
        var task = FXTask.task(() -> prefs.importPreferences(filePath));
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> exportAsync(Path filePath) {
        var task = FXTask.task(() -> prefs.exportPreferences(filePath));
        TaskWorker.getInstance().submit(task);
        return task;
    }

    public FXTask<Void> clearAsync() {
        var task = FXTask.task(prefs::clear);
        TaskWorker.getInstance().submit(task);
        return task;
    }

}
