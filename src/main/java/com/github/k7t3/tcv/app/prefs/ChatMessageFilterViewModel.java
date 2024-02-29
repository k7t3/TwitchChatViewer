package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatMessageFilterPreferences;
import de.saxsys.mvvmfx.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;

public class ChatMessageFilterViewModel implements ViewModel {

    private final ObservableList<String> filters;

    private final ChatMessageFilterPreferences filterPrefs;

    public ChatMessageFilterViewModel() {
        var prefs = AppPreferences.getInstance();
        filterPrefs = prefs.getMessageFilterPreferences();
        filters = FXCollections.observableList(new ArrayList<>(filterPrefs.getMessageFilter().getRegexes()));
    }

    public void sync() {
        var items = filters.stream().filter(f -> !f.trim().isEmpty()).toList();
        var set = filterPrefs.getMessageFilter().getRegexes();
        set.clear();
        set.addAll(items);
        filterPrefs.sync();
    }

    public ObservableList<String> getFilters() {
        return filters;
    }
}
