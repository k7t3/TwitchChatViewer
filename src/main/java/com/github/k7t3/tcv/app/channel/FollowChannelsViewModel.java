package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.prefs.AppPreferences;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.Observable;
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

    private static final Comparator<TwitchChannelViewModel> DEFAULT_COMPARATOR =
            Comparator.comparing(TwitchChannelViewModel::getViewerCount).reversed()
                    .thenComparing(TwitchChannelViewModel::getUserLogin);

    /** フォローしているすべてのチャンネル*/
    private final ObservableList<TwitchChannelViewModel> followChannels =
            FXCollections.observableArrayList(vm ->
                    new Observable[] { vm.liveProperty(), vm.observableViewerCount() }
            );

    /** 並べ替え、フィルタ*/
    private final SortedList<TwitchChannelViewModel> transformedChannels;

    /** ライブ中のみ*/
    private final BooleanProperty onlyLive = new SimpleBooleanProperty(false);

    /** キーワードフィルタ*/
    private final StringProperty filter = new SimpleStringProperty();

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private final ObservableList<TwitchChannelViewModel> selectedChannels = FXCollections.observableArrayList();

    private final BooleanProperty visibleFully = new SimpleBooleanProperty(true);

    private ChatRoomContainerViewModel chatContainerViewModel;

    public FollowChannelsViewModel() {
        var filtered = new FilteredList<>(followChannels);
        filtered.predicateProperty().bind(Bindings.createObjectBinding(() -> this::test, filter, onlyLive));

        transformedChannels = new SortedList<>(filtered);
        transformedChannels.setComparator(DEFAULT_COMPARATOR);

        initialize();
    }

    private void initialize() {
        var helper = AppHelper.getInstance();

        // 認証が解除されたらクリア
        helper.authorizedProperty().addListener((ob, o, n) -> {
            if (!n) {
                followChannels.clear();
                loaded.set(false);
            }
        });
    }

    private boolean test(TwitchChannelViewModel channel) {
        var keyword = getFilter() == null ? "" : getFilter().trim().toLowerCase();
        if (keyword.isEmpty())
            return !isOnlyLive() || channel.isLive();

        var gameTitle = channel.getGameName() == null ? "" : channel.getGameName();

        return (!isOnlyLive() || channel.isLive()) && channel.getUserName().toLowerCase().contains(keyword) ||
                channel.getUserLogin().toLowerCase().contains(keyword) ||
                gameTitle.toLowerCase().contains(keyword);
    }

    public ObservableList<TwitchChannelViewModel> getFollowChannels() {
        return transformedChannels;
    }

    public FXTask<List<TwitchChannelViewModel>> loadAsync() {
        if (isLoaded()) {
            throw new RuntimeException("already loaded");
        }

        LOGGER.info("start loadAsync");

        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();
        var repository = twitch.getChannelRepository();

        var task = FXTask.task(() -> {
            repository.loadAllFollowBroadcasters();
            var channels = repository.getChannels().stream()
                    .map(TwitchChannelViewModel::new)
                    .toList();
            channels.forEach(c -> c.getChannel().addListener(new TwitchChannelStreamListener(c)));
            return channels;
        });
        FXTask.setOnSucceeded(task, e -> {
            LOGGER.info("succeeded to get all followed channel");

            followChannels.setAll(task.getValue());

            loaded.set(true);
        });
        TaskWorker.getInstance().submit(task);

        return task;
    }

    public void installChatContainerViewModel(ChatRoomContainerViewModel chatContainerViewModel) {
        this.chatContainerViewModel = chatContainerViewModel;
    }

    public void joinChat() {
        if (chatContainerViewModel == null)
            throw new IllegalStateException();

        var selectedChannels = getSelectedChannels();
        if (selectedChannels.isEmpty()) return;

        // 選択が一つだけならそのまま開いて終わり
        if (selectedChannels.size() == 1) {
            var channel = selectedChannels.getFirst();
            chatContainerViewModel.register(channel);
            return;
        }

        var prefs = AppPreferences.getInstance().getGeneralPreferences();

        // 開くときの動作
        switch (prefs.getMultipleOpenType()) {

            // まとめて開く
            case MERGED -> {
                chatContainerViewModel.registerAll(selectedChannels);
            }

            // それぞれを分離して開く
            case SEPARATED -> selectedChannels.forEach(channel -> chatContainerViewModel.register(channel));

        }
    }

    public ObservableList<TwitchChannelViewModel> getSelectedChannels() {
        return selectedChannels;
    }

    public ReadOnlyBooleanProperty loadedProperty() { return loaded.getReadOnlyProperty(); }
    public boolean isLoaded() { return loaded.get(); }

    public StringProperty filterProperty() { return filter; }
    public String getFilter() { return filter.get(); }
    public void setFilter(String filter) { this.filter.set(filter); }

    public BooleanProperty onlyLiveProperty() { return onlyLive; }
    public boolean isOnlyLive() { return onlyLive.get(); }
    public void setOnlyLive(boolean onlyLive) { this.onlyLive.set(onlyLive); }

    public BooleanProperty visibleFullyProperty() { return visibleFully; }
    public boolean isVisibleFully() { return visibleFully.get(); }
    public void setVisibleFully(boolean visibleFully) { this.visibleFully.set(visibleFully); }
}
