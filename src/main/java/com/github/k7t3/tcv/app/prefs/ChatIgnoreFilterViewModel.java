package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatIgnoreFilterPreferences;
import de.saxsys.mvvmfx.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;

public class ChatIgnoreFilterViewModel implements ViewModel {

    private final ObservableList<String> filters;

    private final ChatIgnoreFilterPreferences filterPrefs;

    public ChatIgnoreFilterViewModel() {
        var prefs = AppPreferences.getInstance();
        filterPrefs = prefs.getIgnoreFilterPreferences();
        filters = FXCollections.observableList(new ArrayList<>(filterPrefs.getChatFilter().getRegexes()));
    }

    public void sync() {
        var items = filters.stream().filter(f -> !f.trim().isEmpty()).toList();
        var set = filterPrefs.getChatFilter().getRegexes();
        set.clear();
        set.addAll(items);
        filterPrefs.sync();
    }

    public ObservableList<String> getFilters() {
        return filters;
    }
}
