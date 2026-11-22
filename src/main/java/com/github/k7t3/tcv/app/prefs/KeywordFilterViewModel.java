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

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterType;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
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

    public KeywordFilterViewModel(ChatFilters filters) {
        keywordEntries = FXCollections.observableArrayList((w) -> new Observable[] { w.removedProperty() });
        keywordEntries.addAll(filters.getKeywordEntries()
                .stream()
                .map(e -> new Wrapper(e))
                .toList());
        filtered = new FilteredList<>(keywordEntries);
        filtered.setPredicate(w -> !w.isRemoved());
    }

    public ObservableList<Wrapper> getKeywordEntries() {
        return filtered;
    }

    public void addEntry(KeywordFilterEntry entry) {
        keywordEntries.add(new Wrapper(entry));
    }

    @Override
    public boolean canSync() {
        return keywordEntries.stream().filter(w -> !w.isRemoved()).noneMatch(Wrapper::isInvalid);
    }

    @Override
    public void prepare(SettingsDraft.Builder target) {
        if (!canSync()) throw new IllegalStateException("keyword filters contain invalid entries");
        for (var row : keywordEntries) {
            if (row.isRemoved()) { target.deleteKeyword(row.entry.getFilterId()); continue; }
            if (row.isDirty() || row.entry.getFilterId() < 1) {
                target.keyword(new ChatKeywordFilterEntity(Math.max(0, row.entry.getFilterId()), row.getFilterType().getType(), row.getKeyword()));
            }
        }
    }

    public static class Wrapper {
        private final KeywordFilterEntry entry;
        private final ObjectProperty<KeywordFilterType> filterType = new SimpleObjectProperty<>() {
            @Override
            protected void invalidated() {
                markDirty();
                update(getKeyword(), get());
            }
        };
        private final StringProperty keyword = new SimpleStringProperty() {
            @Override
            protected void invalidated() {
                markDirty();
                update(get(), getFilterType());
            }
        };
        private final BooleanProperty removed = new SimpleBooleanProperty() {
            @Override
            protected void invalidated() {
                markDirty();
            }
        };
        private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
        private final ReadOnlyBooleanWrapper invalid = new ReadOnlyBooleanWrapper(true);
        private Pattern patternCache;

        private Wrapper(KeywordFilterEntry entry) {
            this.entry = entry;
            setKeyword(entry.getKeyword());
            setFilterType(entry.getFilterType());

            if (entry.getFilterType() == KeywordFilterType.REGEXP) {
                patternCache = entry.getPattern();
            }
            dirty.set(false);
        }

        private void update(String keyword, KeywordFilterType filterType) {
            if (filterType == null || keyword == null || keyword.trim().isEmpty()) {
                invalid.set(true);
                return;
            }

            if (filterType == KeywordFilterType.REGEXP) {
                updatePattern(keyword);
            } else {
                invalid.set(false);
            }
        }

        private void updatePattern(String source) {
            try {
                patternCache = Pattern.compile(source);
                invalid.set(false);
            } catch (PatternSyntaxException ignored) {
                patternCache = null;
                invalid.set(true);
            }
        }

        private void markDirty() {
            dirty.set(true);
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
