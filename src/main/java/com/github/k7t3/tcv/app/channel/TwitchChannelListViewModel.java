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

package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import java.util.Comparator;
import java.util.Locale;

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

    private final BrowserActions browsers;
    private boolean loggedOut;

    public TwitchChannelListViewModel(BrowserActions browsers) {
        this.browsers = browsers;
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
        if (!liveState || !followingState) return false;

        var keyword = getFilter() == null ? "" : getFilter().trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) {
            return true;
        }

        var gameTitle = channel.getGameName() == null ? "" : channel.getGameName();
        return channel.getUserName().toLowerCase(Locale.ROOT).contains(keyword) ||
               channel.getUserLogin().toLowerCase(Locale.ROOT).contains(keyword) ||
               gameTitle.toLowerCase(Locale.ROOT).contains(keyword);
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
        if (isClosed()) throw new IllegalStateException("channel list is closed");
        if (lazySource != null) Bindings.unbindContent(loadedChannels, lazySource);
        loggedOut = false;
        selectedChannels.clear();
        lazySource = channels;
        Bindings.bindContent(loadedChannels, lazySource);
    }

    public ObservableList<TwitchChannelViewModel> getLoadedChannels() {
        return transformedChannels;
    }

    public void openSelectedChannelPageOnBrowser() {
        checkActive();
        var urls = selectedChannels.stream().map(channel -> "https://www.twitch.tv/" + channel.getBroadcaster().getUserLogin()).toList();
        if (!urls.isEmpty()) browsers.openAll(urls);
    }

    public void joinChat() {
        checkActive();
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
    protected void onLoggedOut() {
        loggedOut = true;
        browsers.cancelAll();
        selectedChannels.clear();
        if (lazySource != null) {
            Bindings.unbindContent(loadedChannels, lazySource);
            lazySource = null;
        }
        loadedChannels.clear();
    }

    private void checkActive() {
        if (isClosed() || loggedOut) throw new IllegalStateException("channel list is no longer active");
    }

    @Override protected void onClose() {
        CloseActions.runAll(this::onLoggedOut, browsers::close,
                () -> multipleOpenType.unbind(), () -> transformedChannels.predicateProperty().unbind());
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
