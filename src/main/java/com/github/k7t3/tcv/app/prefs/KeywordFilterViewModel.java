package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterType;
import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class KeywordFilterViewModel implements PreferencesViewModelBase {

    private final ObservableList<Wrapper> keywordEntries;
    private final FilteredList<Wrapper> filtered;

    private final ChatFilters chatFilters;

    public KeywordFilterViewModel(ChatFilters filters) {
        this.chatFilters = filters;
        keywordEntries = FXCollections.observableArrayList((w) -> new Observable[] { w.removedProperty() });
        keywordEntries.addAll(filters.getKeywordEntries()
                .stream()
                .map(e -> new Wrapper(e, chatFilters))
                .toList());
        filtered = new FilteredList<>(keywordEntries);
        filtered.setPredicate(w -> !w.isRemoved());
    }

    public ObservableList<Wrapper> getKeywordEntries() {
        return filtered;
    }

    public void addEntry(KeywordFilterEntry entry) {
        keywordEntries.add(new Wrapper(entry, chatFilters));
    }

    @Override
    public boolean canSync() {
        return keywordEntries.stream().noneMatch(Wrapper::isInvalid);
    }

    @Override
    public void sync() {
        keywordEntries.stream().filter(Wrapper::isDirty).forEach(Wrapper::save);
    }

    public static class Wrapper {
        private final KeywordFilterEntry entry;
        private final ChatFilters chatFilters;
        private final ObjectProperty<KeywordFilterType> filterType = new SimpleObjectProperty<>() {
            @Override
            protected void invalidated() {
                markDirty();
            }
        };
        private final StringProperty keyword = new SimpleStringProperty() {
            @Override
            protected void invalidated() {
                markDirty();

                if (getFilterType() == KeywordFilterType.REGEXP) {
                    try {
                        patternCache = Pattern.compile(get());
                        invalid.set(false);
                    } catch (PatternSyntaxException ignored) {
                        patternCache = null;
                        invalid.set(true);
                    }
                } else {
                    invalid.set(false);
                }
            }
        };
        private final BooleanProperty removed = new SimpleBooleanProperty() {
            @Override
            protected void invalidated() {
                markDirty();
            }
        };
        private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
        private final ReadOnlyBooleanWrapper invalid = new ReadOnlyBooleanWrapper();
        private Pattern patternCache;

        private Wrapper(KeywordFilterEntry entry, ChatFilters chatFilters) {
            this.entry = entry;
            this.chatFilters = chatFilters;
            setKeyword(entry.getKeyword());
            setFilterType(entry.getFilterType());

            if (entry.getFilterType() == KeywordFilterType.REGEXP) {
                patternCache = entry.getPattern();
            }
        }

        private void markDirty() {
            dirty.set(true);
        }

        private void save() {
            if (!isDirty()) return;
            if (isRemoved()) {
                chatFilters.deleteKeywordFilter(entry);
                return;
            }

            var keyword = getKeyword();
            if (getFilterType() == KeywordFilterType.REGEXP) {
                if (!isInvalid()) {
                    entry.setKeyword(keyword);
                    entry.setPattern(patternCache);
                    entry.setFilterType(getFilterType());
                    chatFilters.saveKeywordFilter(entry);
                }
            } else {
                entry.setKeyword(keyword);
                entry.setFilterType(getFilterType());
                chatFilters.saveKeywordFilter(entry);
            }
        }

        public ReadOnlyBooleanProperty dirtyProperty() { return dirty.getReadOnlyProperty(); }
        public boolean isDirty() { return dirty.get(); }

        public ReadOnlyBooleanProperty invalidProperty() { return invalid.getReadOnlyProperty(); }
        public boolean isInvalid() { return invalid.get(); }

        public BooleanProperty removedProperty() { return removed; }
        public boolean isRemoved() { return removed.get(); }
        public void setRemoved(boolean removed) { this.removed.set(removed); }

        public StringProperty keywordProperty() { return keyword; }
        public String getKeyword() { return keyword.get(); }
        public void setKeyword(String keyword) { this.keyword.set(keyword); }

        public ObjectProperty<KeywordFilterType> filterTypeProperty() { return filterType; }
        public KeywordFilterType getFilterType() { return filterType.get(); }
        public void setFilterType(KeywordFilterType filterType) { this.filterType.set(filterType); }
    }

}
