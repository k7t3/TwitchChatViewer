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

package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.beans.Observable;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.Comparator;

public class ChannelGroupListViewModel extends AbstractViewModel {

    private final ChannelGroupRepository repository;
    private final ObservableList<ChannelGroup> source;
    private final ObservableList<ChannelGroup> items = FXCollections.observableArrayList(
            group -> new Observable[] { group.pinnedProperty(), group.nameProperty(), group.updatedAtProperty() });
    private final SortedList<ChannelGroup> sorted;
    private final FilteredList<ChannelGroup> filtered;
    private final StringProperty filter = new SimpleStringProperty();

    public ChannelGroupListViewModel(ChannelGroupRepository repository) {
        this.repository = repository;
        source = repository.getAll();
        Bindings.bindContent(items, source);
        sorted = new SortedList<>(items);
        filtered = new FilteredList<>(sorted);
        filtered.predicateProperty().bind(Bindings.createObjectBinding(() -> this::test, filter));
    }

    /**
     * チャンネルグループに対するテスト
     */
    private boolean test(ChannelGroup group) {
        var filter = getFilter() == null ? "" : getFilter().trim().toLowerCase();
        if (filter.isEmpty()) return true;

        if (group.getName().toLowerCase().contains(filter)) {
            return true;
        }

        if (group.getComment().toLowerCase().contains(filter)) {
            return true;
        }

        return group.getChannels().stream().anyMatch(c -> test(c, filter));
    }

    /**
     * チャンネルに対するテスト
     */
    private boolean test(TwitchChannelViewModel channel, String filter) {
        var gameTitle = channel.getGameName() == null ? "" : channel.getGameName();
        return channel.getUserName().toLowerCase().contains(filter) ||
                channel.getUserLogin().toLowerCase().contains(filter) ||
                gameTitle.toLowerCase().contains(filter);
    }

    public void setComparator(Comparator<ChannelGroup> comparator) {
        checkOpen();
        sorted.setComparator(comparator == null ? ChannelGroupPinnedComparator.INSTANCE
                : ChannelGroupPinnedComparator.INSTANCE.thenComparing(comparator));
    }

    public ObservableList<ChannelGroup> getChannelGroups() {
        return filtered;
    }

    public FXTask<?> create(String name) {
        checkOpen();
        var group = new ChannelGroup();
        group.setName(name);
        return repository.saveAsync(group);
    }

    public FXTask<?> update(ChannelGroup group) {
        checkOpen();
        return repository.saveAsync(group);
    }

    public FXTask<?> delete(ChannelGroup group) {
        checkOpen();
        return repository.removeAsync(group);
    }

    public StringProperty filterProperty() { return filter; }
    public String getFilter() { return filter.get(); }
    public void setFilter(String filter) { this.filter.set(filter); }

    private void checkOpen() { if (isClosed()) throw new IllegalStateException("group editor is closed"); }

    @Override protected void onClose() {
        Bindings.unbindContent(items, source);
        filtered.predicateProperty().unbind();
        filter.unbind();
        items.clear();
    }

}
