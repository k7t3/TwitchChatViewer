package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.Collections;
import java.util.Comparator;

/**
 * 永続化しているチャンネルのリスト
 * <p>
 *     {@link #filterProperty()}
 * </p>
 */
public class TwitchChannelListViewModel extends AbstractViewModel {

    /**
     * チャンネルリストのComparator
     * 視聴者数の降順でログインしている人を優先
     */
    private static final Comparator<TwitchChannelViewModel> DEFAULT_COMPARATOR =
            Comparator.comparing(TwitchChannelViewModel::getViewerCount).reversed()
                    .thenComparing(TwitchChannelViewModel::getUserLogin);

    /** loadedChannelsにバインドするソース*/
    private ObservableList<TwitchChannelViewModel> lazySource = null;

    /** ロードしているすべてのチャンネル*/
    private final ObservableList<TwitchChannelViewModel> loadedChannels =
            FXCollections.observableArrayList(vm ->
                    new Observable[] { vm.liveProperty(), vm.observableViewerCount(), vm.persistentProperty() }
            );

    private final FilteredList<TwitchChannelViewModel> transformedChannels;
    private final ObservableList<TwitchChannelViewModel> selectedChannels = FXCollections.observableArrayList();

    private final BooleanProperty onlyLive = new SimpleBooleanProperty(false);
    private final BooleanProperty onlyFollow = new SimpleBooleanProperty(false);
    private final StringProperty filter = new SimpleStringProperty();
    private final ObjectProperty<MultipleChatOpenType> multipleOpenType = new SimpleObjectProperty<>(MultipleChatOpenType.SEPARATED);

    public TwitchChannelListViewModel() {
        var sorted = new SortedList<>(loadedChannels);
        sorted.setComparator(DEFAULT_COMPARATOR);

        var persistent = new FilteredList<>(sorted);
        persistent.setPredicate(TwitchChannelViewModel::isPersistent);

        transformedChannels = new FilteredList<>(persistent);
        transformedChannels.predicateProperty().bind(Bindings.createObjectBinding(() -> this::filter, filter, onlyLive, onlyFollow));
    }

    public void bindGeneralPreferences(GeneralPreferences preferences) {
        multipleOpenType.bind(preferences.multipleOpenTypeProperty());
    }

    private boolean filter(TwitchChannelViewModel channel) {
        var liveState = !isOnlyLive() || channel.isLive();
        var followingState = !isOnlyFollow() || channel.isFollowing();

        var keyword = getFilter() == null ? "" : getFilter().trim().toLowerCase();
        if (keyword.isEmpty()) {
            return liveState && followingState;
        }

        var gameTitle = channel.getGameName() == null ? "" : channel.getGameName();
        return (liveState && followingState) && channel.getUserName().toLowerCase().contains(keyword) ||
               channel.getUserLogin().toLowerCase().contains(keyword) ||
               gameTitle.toLowerCase().contains(keyword);
    }

    /**
     * ロード済みのチャンネル一覧としてバインドするソースコレクションを指定する。
     * <p>
     *     ソースチャンネルのうち、{@link TwitchChannelViewModel#persistentProperty()}が
     *     有効なものが{@link #getLoadedChannels()}で取得できるようになる。
     * </p>
     * @param channels バインドソース
     */
    public void bindChannels(ObservableList<TwitchChannelViewModel> channels) {
        lazySource = channels;
        Bindings.bindContent(loadedChannels, lazySource);
    }

    public ObservableList<TwitchChannelViewModel> getLoadedChannels() {
        return transformedChannels;
    }

    public void openSelectedChannelPageOnBrowser() {
        var selectedChannels = getSelectedChannels();
        if (selectedChannels.isEmpty()) return;

        var channels = Collections.unmodifiableList(selectedChannels);

        FXTask.task(() -> {
            channels.forEach(TwitchChannelViewModel::openChannelPageOnBrowser);
        }).runAsync();
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
            var openType = multipleOpenType.get();
            openingEvent = new ChatOpeningEvent(openType, getSelectedChannels());
        }

        // イベントを発行
        publish(openingEvent);
    }

    public ObservableList<TwitchChannelViewModel> getSelectedChannels() {
        return selectedChannels;
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        // no-op
    }

    @Override
    public void onLogout() {
        if (lazySource != null) {
            Bindings.unbindContent(loadedChannels, lazySource);
            lazySource = null;
        }
        loadedChannels.clear();
    }

    @Override
    public void close() {
        // no-op
    }

    public StringProperty filterProperty() { return filter; }
    public String getFilter() { return filter.get(); }
    public void setFilter(String filter) { this.filter.set(filter); }

    public BooleanProperty onlyLiveProperty() { return onlyLive; }
    public boolean isOnlyLive() { return onlyLive.get(); }
    public void setOnlyLive(boolean onlyLive) { this.onlyLive.set(onlyLive); }

    public BooleanProperty onlyFollowProperty() { return onlyFollow; }
    public boolean isOnlyFollow() { return onlyFollow.get(); }
    public void setOnlyFollow(boolean onlyFollow) { this.onlyFollow.set(onlyFollow); }

}
