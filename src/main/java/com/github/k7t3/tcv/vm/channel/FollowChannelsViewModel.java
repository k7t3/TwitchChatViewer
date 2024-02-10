package com.github.k7t3.tcv.vm.channel;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.vm.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.service.FXTask;
import com.github.k7t3.tcv.vm.service.TaskWorker;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.events.ChannelViewerCountUpdateEvent;
import com.github.twitch4j.eventsub.events.ChannelFollowEvent;
import de.saxsys.mvvmfx.SceneLifecycle;
import de.saxsys.mvvmfx.ViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FollowChannelsViewModel implements ViewModel, SceneLifecycle {

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

    private final ObjectProperty<ChannelRepository> channelRepository = new SimpleObjectProperty<>(null);

    private final ObjectProperty<FollowChannelViewModel> selectedBroadcaster = new SimpleObjectProperty<>(null);

    private ChatContainerViewModel chatContainerViewModel;

    private List<IEventSubscription> eventSubscriptions;

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

        var repository = getChannelRepository();

        if (repository == null) {
            throw new RuntimeException("channel repository is null");
        }

        var task = FXTask.task(() -> {
            repository.loadAllFollowBroadcasters();
            return repository.getChannels().stream()
                    .map(FollowChannelViewModel::new)
                    .toList();
        });
        FXTask.setOnSucceeded(task, e -> {
            LOGGER.info("succeeded to get all followed channel");

            var client = AppHelper.getInstance().getTwitch().getClient();
            var evm = client.getEventManager();

            followBroadcasters.setAll(task.getValue());

            eventSubscriptions = new ArrayList<>();
            eventSubscriptions.add(evm.onEvent(ChannelGoLiveEvent.class, ev -> Platform.runLater(() -> handleOnline(ev))));
            eventSubscriptions.add(evm.onEvent(ChannelGoOfflineEvent.class, ev -> Platform.runLater(() -> handleOffline(ev))));
            eventSubscriptions.add(evm.onEvent(ChannelViewerCountUpdateEvent.class, ev -> Platform.runLater(() -> handleViewerCountUpdate(ev))));

            // フォローしたときのイベントを登録
            // TODO フォロー解除したときのイベントは？
            eventSubscriptions.add(evm.onEvent(ChannelFollowEvent.class, this::handleFollowChannel));

            loaded.set(true);
        });
        TaskWorker.getInstance().submit(task);

        return task;
    }

    private void handleOnline(ChannelGoLiveEvent e) {
        var stream = e.getStream();

        for (var broadcaster : followBroadcasters) {
            if (broadcaster.getUserId().equalsIgnoreCase(stream.getUserId())) {
                broadcaster.getChannel().setStream(stream);
                broadcaster.goOnline();
                break;
            }
        }
    }

    private void handleOffline(ChannelGoOfflineEvent e) {
        var userId = e.getChannel().getId();

        for (var broadcaster : followBroadcasters) {
            if (broadcaster.getUserId().equalsIgnoreCase(userId)) {
                broadcaster.getChannel().setStream(null);
                broadcaster.goOffline();
                break;
            }
        }
    }

    private void handleViewerCountUpdate(ChannelViewerCountUpdateEvent e) {
        var userId = e.getChannel().getId();

        for (var broadcaster : followBroadcasters) {
            if (broadcaster.getUserId().equalsIgnoreCase(userId)) {
                broadcaster.setViewerCount(e.getViewerCount());
                break;
            }
        }
    }

    private void handleFollowChannel(ChannelFollowEvent e) {
        var broadcaster = new Broadcaster(e.getUserId(), e.getUserLogin(), e.getUserName());
        var repository = getChannelRepository();

        if (repository == null) {
            LOGGER.error("repository is null", new RuntimeException("repository is null"));
            return;
        }

        var channel = repository.registerBroadcaster(broadcaster);
        var vm = new FollowChannelViewModel(channel);

        Platform.runLater(() -> followBroadcasters.add(vm));
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

    public void unsubscribe() {
        if (!isLoaded()) return;

        eventSubscriptions.forEach(IDisposable::dispose);
        eventSubscriptions.clear();
    }

    @Override
    public void onViewAdded() {
    }

    @Override
    public void onViewRemoved() {
        unsubscribe();
    }

    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }

    public StringProperty filterProperty() { return filter; }
    public String getFilter() { return filter.get(); }
    public void setFilter(String filter) { this.filter.set(filter); }

    public BooleanProperty onlyLiveProperty() { return onlyLive; }
    public boolean isOnlyLive() { return onlyLive.get(); }
    public void setOnlyLive(boolean onlyLive) { this.onlyLive.set(onlyLive); }

    public ObjectProperty<ChannelRepository> channelRepositoryProperty() { return channelRepository; }
    public ChannelRepository getChannelRepository() { return channelRepository.get(); }
    public void setChannelRepository(ChannelRepository channelRepository) { this.channelRepository.set(channelRepository); }

    public ObjectProperty<FollowChannelViewModel> selectedBroadcasterProperty() { return selectedBroadcaster; }
    public FollowChannelViewModel getSelectedBroadcaster() { return selectedBroadcaster.get(); }
    public void setSelectedBroadcaster(FollowChannelViewModel selectedBroadcaster) { this.selectedBroadcaster.set(selectedBroadcaster); }
}
