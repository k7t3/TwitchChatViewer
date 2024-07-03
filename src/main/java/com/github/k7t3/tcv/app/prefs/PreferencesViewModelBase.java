package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.core.AppViewModel;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import de.saxsys.mvvmfx.ViewModel;

public interface PreferencesViewModelBase extends AppViewModel {

    void sync();

    boolean canSync();

    @Override
    default void subscribeEvents(EventSubscribers eventSubscribers) {
    }

    @Override
    default void onLogout() {
    }

    @Override
    default void close() {
    }

}
