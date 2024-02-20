package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
import de.saxsys.mvvmfx.ViewModel;

public class PreferencesViewModel implements ViewModel {

    public void saveAsync() {
        var prefs = AppPreferences.getInstance();
        var task = FXTask.task(prefs::save);
        TaskWorker.getInstance().submit(task);
    }

}
