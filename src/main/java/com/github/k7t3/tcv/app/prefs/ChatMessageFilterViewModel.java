package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatMessageFilterPreferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;

public class ChatMessageFilterViewModel implements PreferencesViewModelBase {

    private final ObservableList<String> filters;

    private final ChatMessageFilterPreferences filterPrefs;

    public ChatMessageFilterViewModel() {
        var prefs = AppPreferences.getInstance();
        filterPrefs = prefs.getMessageFilterPreferences();
        filters = FXCollections.observableList(new ArrayList<>(filterPrefs.getKeywordMessageFilter().getKeywords()));
    }

    @Override
    public boolean canSync() {
        return true;
    }

    public void sync() {
        var items = filters.stream().filter(f -> !f.trim().isEmpty()).toList();
        var set = filterPrefs.getKeywordMessageFilter().getKeywords();
        set.clear();
        set.addAll(items);
        filterPrefs.writeToPreferences();
    }

    public ObservableList<String> getFilters() {
        return filters;
    }
}
