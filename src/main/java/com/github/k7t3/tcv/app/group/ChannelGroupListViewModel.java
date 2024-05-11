package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.service.FXTask;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.Comparator;

public class ChannelGroupListViewModel implements ViewModel {

    private final ChannelGroupRepository repository;

    private final SortedList<ChannelGroup> sorted;

    private final FilteredList<ChannelGroup> filtered;

    private final StringProperty filter = new SimpleStringProperty();

    public ChannelGroupListViewModel(ChannelGroupRepository repository) {
        this.repository = repository;
        sorted = new SortedList<>(repository.getAll());
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
        sorted.setComparator(comparator);
    }

    public ObservableList<ChannelGroup> getChannelGroups() {
        return filtered;
    }

    public FXTask<?> create(String name) {
        var group = new ChannelGroup();
        group.setName(name);
        return repository.saveAsync(group);
    }

    public FXTask<?> update(ChannelGroup group) {
        return repository.saveAsync(group);
    }

    public FXTask<?> delete(ChannelGroup group) {
        return repository.removeAsync(group);
    }

    public StringProperty filterProperty() { return filter; }
    public String getFilter() { return filter.get(); }
    public void setFilter(String filter) { this.filter.set(filter); }

    public enum Comparators implements Comparator<ChannelGroup> {

        UPDATED_AT("group.comparator.updated", Comparator.comparing(ChannelGroup::getUpdatedAt)),

        CREATED_AT("group.comparator.created", Comparator.comparing(ChannelGroup::getCreatedAt)),

        LIVE_COUNT("group.comparator.liver", Comparator.comparingLong(g -> g.getChannels()
                .stream()
                .filter(TwitchChannelViewModel::isLive)
                .count()
        )),

        NAME("group.comparator.name", Comparator.comparing(ChannelGroup::getName));

        private final String display;

        private final Comparator<ChannelGroup> comparator;

        Comparators(String display, Comparator<ChannelGroup> comparator) {
            this.display = Resources.getString(display);
            this.comparator = comparator;
        }

        @Override
        public String toString() {
            return display;
        }

        @Override
        public int compare(ChannelGroup o1, ChannelGroup o2) {
            return comparator.compare(o1, o2);
        }
    }

}
