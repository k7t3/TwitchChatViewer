package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class FollowChannelsViewModel implements ViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(FollowChannelsViewModel.class);

    private static final Comparator<FollowChannelViewModel> DEFAULT_COMPARATOR =
            Comparator.comparing(FollowChannelViewModel::getViewerCount).reversed()
                    .thenComparing(FollowChannelViewModel::getUserLogin);

    /** フォローしているすべてのブロードキャスター*/
    private final ObservableList<FollowChannelViewModel> followBroadcasters = FXCollections.observableArrayList();

    /** 並べ替え、フィルタ*/
    private final SortedList<FollowChannelViewModel> transformedBroadcasters;

    /** ライブ中のみ*/
    private final BooleanProperty onlyLive = new SimpleBooleanProperty(false);

    /** キーワードフィルタ*/
    private final StringProperty filter = new SimpleStringProperty();

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private final ObjectProperty<FollowChannelViewModel> selectedBroadcaster = new SimpleObjectProperty<>(null);

    private ChatContainerViewModel chatContainerViewModel;

    public FollowChannelsViewModel() {
        var filtered = new FilteredList<>(followBroadcasters);
        filtered.predicateProperty().bind(Bindings.createObjectBinding(() -> this::test, filter, onlyLive));

        transformedBroadcasters = new SortedList<>(filtered);
        transformedBroadcasters.setComparator(DEFAULT_COMPARATOR);
    }

    private boolean test(FollowChannelViewModel channel) {
        var keyword = getFilter() == null ? "" : getFilter().trim().toLowerCase();
        if (keyword.isEmpty())
            return !isOnlyLive() || channel.isLive();

        var gameTitle = channel.getGameName() == null ? "" : channel.getGameName();

        return (!isOnlyLive() || channel.isLive()) && channel.getUserName().toLowerCase().contains(keyword) ||
                channel.getUserLogin().toLowerCase().contains(keyword) ||
                gameTitle.toLowerCase().contains(keyword);
    }

    public ObservableList<FollowChannelViewModel> getFollowBroadcasters() {
        return transformedBroadcasters;
    }

    public FXTask<List<FollowChannelViewModel>> loadAsync() {
        if (isLoaded()) {
            throw new RuntimeException("already loaded");
        }

        LOGGER.info("start loadAsync");

        var helper = AppHelper.getInstance();
        var repository = helper.getTwitch().getChannelRepository();

        if (repository == null) {
            throw new RuntimeException("channel repository is null");
        }

        var task = FXTask.task(() -> {
            repository.loadAllFollowBroadcasters();
            return repository.getChannels().stream()
                    .map(c -> new FollowChannelViewModel(this, c))
                    .toList();
        });
        FXTask.setOnSucceeded(task, e -> {
            LOGGER.info("succeeded to get all followed channel");

            followBroadcasters.setAll(task.getValue());

            loaded.set(true);
        });
        TaskWorker.getInstance().submit(task);

        return task;
    }

    public void setChatContainerViewModel(ChatContainerViewModel chatContainerViewModel) {
        this.chatContainerViewModel = chatContainerViewModel;
    }

    public void joinChat() {
        if (chatContainerViewModel == null)
            throw new IllegalStateException();

        var selectedBroadcaster = getSelectedBroadcaster();
        if (selectedBroadcaster == null) return;

        var channel = selectedBroadcaster.getChannel();

        chatContainerViewModel.register(channel);
    }

    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }

    public StringProperty filterProperty() { return filter; }
    public String getFilter() { return filter.get(); }
    public void setFilter(String filter) { this.filter.set(filter); }

    public BooleanProperty onlyLiveProperty() { return onlyLive; }
    public boolean isOnlyLive() { return onlyLive.get(); }
    public void setOnlyLive(boolean onlyLive) { this.onlyLive.set(onlyLive); }

    public ObjectProperty<FollowChannelViewModel> selectedBroadcasterProperty() { return selectedBroadcaster; }
    public FollowChannelViewModel getSelectedBroadcaster() { return selectedBroadcaster.get(); }
    public void setSelectedBroadcaster(FollowChannelViewModel selectedBroadcaster) { this.selectedBroadcaster.set(selectedBroadcaster); }
}
