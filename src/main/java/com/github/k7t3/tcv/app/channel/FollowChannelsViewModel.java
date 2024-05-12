package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.event.EventBus;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
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

    /**
     * フォローしているチャンネルのComparator
     * 視聴者数の降順でログインしている人を優先
     */
    private static final Comparator<TwitchChannelViewModel> DEFAULT_COMPARATOR =
            Comparator.comparing(TwitchChannelViewModel::getViewerCount).reversed()
                    .thenComparing(TwitchChannelViewModel::getUserLogin);

    /** フォローしているすべてのチャンネル*/
    private final ObservableList<TwitchChannelViewModel> followChannels =
            FXCollections.observableArrayList(vm ->
                    new Observable[] { vm.liveProperty(), vm.observableViewerCount() }
            );

    /** 並べ替え、フィルタ*/
    private final FilteredList<TwitchChannelViewModel> transformedChannels;

    /** ライブ中のみ*/
    private final BooleanProperty onlyLive = new SimpleBooleanProperty(false);

    /** キーワードフィルタ*/
    private final StringProperty filter = new SimpleStringProperty();

    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);

    private final ObservableList<TwitchChannelViewModel> selectedChannels = FXCollections.observableArrayList();

    private final BooleanProperty visibleFully = new SimpleBooleanProperty(true);

    private final GeneralPreferences generalPrefs;

    public FollowChannelsViewModel(GeneralPreferences generalPrefs) {
        this.generalPrefs = generalPrefs;
        var sorted = new SortedList<>(followChannels);
        sorted.setComparator(DEFAULT_COMPARATOR);

        transformedChannels = new FilteredList<>(sorted);
        transformedChannels.predicateProperty().bind(Bindings.createObjectBinding(() -> this::filter, filter, onlyLive));

        var eventBus = EventBus.getInstance();
        eventBus.subscribe(LogoutEvent.class, e -> {
            // ログアウトしたときはクリアする
            followChannels.clear();
            loaded.set(false);
        });
    }

    private boolean filter(TwitchChannelViewModel channel) {
        var keyword = getFilter() == null ? "" : getFilter().trim().toLowerCase();
        if (keyword.isEmpty())
            return !isOnlyLive() || channel.isLive();

        var gameTitle = channel.getGameName() == null ? "" : channel.getGameName();

        return (!isOnlyLive() || channel.isLive()) && channel.getUserName().toLowerCase().contains(keyword) ||
                channel.getUserLogin().toLowerCase().contains(keyword) ||
                gameTitle.toLowerCase().contains(keyword);
    }

    public void setFollowChannels(List<TwitchChannelViewModel> channels) {
        this.followChannels.setAll(channels);
        loaded.set(true);
    }

    public ObservableList<TwitchChannelViewModel> getFollowChannels() {
        return transformedChannels;
    }

    public void joinChat() {
        var selectedChannels = getSelectedChannels();
        if (selectedChannels.isEmpty()) return;

        // チャットを開くイベント
        ChatOpeningEvent openingEvent;

        // 選択が一つだけならそのまま開いて終わり
        if (selectedChannels.size() == 1) {
            var channel = selectedChannels.getFirst();
            // チャットを開くイベントを発行
            openingEvent = new ChatOpeningEvent(channel);
        } else {
            // 開くときの動作を取得
            var openType = generalPrefs.getMultipleOpenType();
            openingEvent = new ChatOpeningEvent(openType, getSelectedChannels());
        }

        // イベントを発行
        var eventBus = EventBus.getInstance();
        eventBus.publish(openingEvent);
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
